package com.han1997.vipledger.core.data.importexport

import com.han1997.vipledger.core.model.TransactionType
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CsvField { TIME, AMOUNT, TYPE, ACCOUNT, TITLE, REMARK }

data class CsvMapping(val columns: Map<CsvField, Int>) {
    fun missingRequired(): Set<CsvField> = setOf(CsvField.TIME, CsvField.AMOUNT, CsvField.TITLE)
        .filterNot(columns::containsKey).toSet()
}

data class ImportRow(
    val occurredAt: Long,
    val amountCents: Long,
    val type: TransactionType,
    val title: String,
    val remark: String,
    val sourceLine: Int,
)

data class CsvPreview(
    val headers: List<String>,
    val rows: List<List<String>>,
    val mapping: CsvMapping,
    val invalidFields: Set<CsvField>,
)

data class CsvParseResult(val accepted: List<ImportRow>, val failedLines: List<Int>)
data class ImportReport(val inserted: Int, val duplicates: Int, val failedLines: List<Int>)

/**
 * 支付宝/微信/通用 CSV 解析。
 *
 * 契约（`.trellis/spec/android/import-export.md` 导入一节）：
 * 预览最多 [PREVIEW_LIMIT] 行并逐字段校验必填项；解析失败的行只记源文件行号，
 * 去重键为 `sha1(发生时间分钟 + 金额分 + 账户 + 标题)`（账户由调用方在导入时提供）。
 * 解析本身不做任何落库，原子性由 [com.han1997.vipledger.core.data.backup.BackupIoManager.importCsv] 的事务保证。
 */
object CsvLedgerImporter {

    /**
     * 账单字段 → 候选关键词。`presetMapping` 的列匹配与 [headerIndex] 的行打分共用这一份，
     * 两处判据不允许各自维护，否则「预览认的表头」和「预设映射认的列」会漂移。
     */
    private val HEADER_CANDIDATES: Map<CsvField, List<String>> = mapOf(
        CsvField.TIME to listOf("交易时间", "发生时间", "创建时间", "时间", "日期", "date"),
        CsvField.AMOUNT to listOf("金额", "金额(元)", "金额（元）", "amount"),
        CsvField.TYPE to listOf("收/支", "收支", "交易类型", "类型", "收付款"),
        CsvField.TITLE to listOf("商品", "商品名称", "交易对方", "商户", "摘要", "标题", "商品说明", "name"),
        CsvField.REMARK to listOf("备注", "描述", "remark"),
        CsvField.ACCOUNT to listOf("支付方式", "收/付款方式", "付款方式", "账户"),
    )

    fun preview(text: String, preset: String? = null, mapping: CsvMapping? = null): CsvPreview {
        val records = parseCsv(text)
        val headerIndex = headerIndex(records)
        val headers = records.getOrNull(headerIndex).orEmpty()
        val resolved = mapping ?: presetMapping(headers, preset)
        val sample = dataRows(records, headerIndex).take(PREVIEW_LIMIT)
        val invalid = resolved.missingRequired().toMutableSet()
        if (resolved.missingRequired().isEmpty()) {
            sample.forEach { row -> invalid += invalidFieldsOf(row, resolved) }
        }
        return CsvPreview(headers, sample, resolved, invalid)
    }

    fun parse(text: String, mapping: CsvMapping, preset: String? = null): CsvParseResult {
        require(mapping.missingRequired().isEmpty()) { "Required CSV columns are missing" }
        val records = parseCsv(text)
        val headerIndex = headerIndex(records)
        val accepted = mutableListOf<ImportRow>()
        val failures = mutableListOf<Int>()
        records.drop(headerIndex + 1).forEachIndexed { index, row ->
            if (row.all(String::isBlank)) return@forEachIndexed
            val line = headerIndex + index + 2
            runCatching { toRow(row, mapping, line) }
                .onSuccess(accepted::add)
                .onFailure { failures += line }
        }
        return CsvParseResult(accepted, failures)
    }

    fun presetMapping(headers: List<String>, preset: String? = null): CsvMapping {
        val normalized = headers.map(::normalizeHeader)
        fun find(field: CsvField): Int? {
            val candidates = HEADER_CANDIDATES.getValue(field)
            return normalized.indexOfFirst { header -> candidates.any(header::contains) }.takeIf { it >= 0 }
        }
        return CsvMapping(
            CsvField.entries.mapNotNull { field -> find(field)?.let { field to it } }.toMap(),
        )
    }

    fun fingerprint(occurredAt: Long, amountCents: Long, accountId: String, title: String): String {
        val source = "${occurredAt / 60_000}|$amountCents|$accountId|$title".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-1").digest(source).joinToString("") { "%02x".format(it) }
    }

    private fun dataRows(records: List<List<String>>, headerIndex: Int): List<List<String>> =
        records.drop(headerIndex + 1).filter { row -> row.any(String::isNotBlank) }

    /**
     * 表头行探测：预览与解析共用同一套判据，避免两处结果不一致。
     *
     * 真实微信/支付宝账单在表头前有元数据前导行，其中「起始时间/终止时间」这类行也会命中关键词「时间」，
     * 旧的「任一单元格含时间/日期即算表头」的启发式会把它当成表头，导致列映射整体错位。
     * 因此改为按「一行命中了多少个**不同**账单字段」打分（候选词与 [HEADER_CANDIDATES] 同源），取最高分行；
     * 最高分低于 [MIN_HEADER_MATCHES] 时回退到旧的单词命中启发式，保证既有可用文件不回归。
     */
    private fun headerIndex(records: List<List<String>>): Int {
        val best = records
            .mapIndexed { index, row -> index to matchedFieldCount(row) }
            .maxByOrNull { it.second }
        if (best != null && best.second >= MIN_HEADER_MATCHES) return best.first
        return records.indexOfFirst { row ->
            row.any {
                it.contains("时间") || it.contains("日期") || it.contains("交易时间") || it.equals("date", true)
            }
        }.coerceAtLeast(0)
    }

    /** 一行命中多少个**不同**账单字段：同一字段命中多次只记一次，避免重复列把分数刷上去。 */
    private fun matchedFieldCount(row: List<String>): Int {
        val normalized = row.map(::normalizeHeader)
        return HEADER_CANDIDATES.count { (_, candidates) ->
            normalized.any { cell -> candidates.any(cell::contains) }
        }
    }

    private fun normalizeHeader(value: String): String =
        value.trim().trimStart(BOM_CHAR).lowercase(Locale.ROOT)

    /** 只对**确实存在**的列做校验，逐字段归因，不再把所有失败都算到 TIME/AMOUNT 头上。 */
    private fun invalidFieldsOf(values: List<String>, mapping: CsvMapping): Set<CsvField> {
        val invalid = mutableSetOf<CsvField>()
        mapping.columns[CsvField.TIME]?.let {
            runCatching { parseDate(value(values, mapping, CsvField.TIME)) }.onFailure { _ -> invalid += CsvField.TIME }
        }
        mapping.columns[CsvField.AMOUNT]?.let {
            runCatching { parseAmount(value(values, mapping, CsvField.AMOUNT)) }.onFailure { _ -> invalid += CsvField.AMOUNT }
        }
        val title = value(values, mapping, CsvField.TITLE).ifBlank { value(values, mapping, CsvField.REMARK) }
        if (mapping.columns[CsvField.TITLE] != null && title.isBlank()) invalid += CsvField.TITLE
        return invalid
    }

    private fun toRow(values: List<String>, mapping: CsvMapping, line: Int): ImportRow {
        val occurredAt = parseDate(value(values, mapping, CsvField.TIME))
        val amount = parseAmount(value(values, mapping, CsvField.AMOUNT))
        val typeText = value(values, mapping, CsvField.TYPE)
        val type = when {
            typeText.contains("收入") || typeText.contains("收款") || typeText.contains("转入") -> TransactionType.INCOME
            typeText.contains("转账") || typeText.contains("还款") -> throw IllegalArgumentException("Unsupported transaction kind")
            typeText.contains("支出") || typeText.contains("付款") || typeText.contains("消费") || typeText.contains("成功") ->
                TransactionType.EXPENSE
            else -> TransactionType.EXPENSE
        }
        val title = value(values, mapping, CsvField.TITLE).ifBlank { value(values, mapping, CsvField.REMARK) }
        require(title.isNotBlank()) { "Missing title" }
        return ImportRow(occurredAt, amount, type, title, value(values, mapping, CsvField.REMARK), line)
    }

    private fun value(values: List<String>, mapping: CsvMapping, field: CsvField): String =
        values.getOrNull(mapping.columns[field] ?: -1)?.trim().orEmpty()

    private fun parseAmount(raw: String): Long {
        val normalized = raw.replace(",", "").replace("￥", "").replace("¥", "").trim()
        require(normalized.isNotEmpty()) { "Missing amount" }
        val cents = BigDecimal(normalized).movePointRight(2).longValueExact()
        require(cents != 0L) { "Zero amount" }
        return cents
    }

    private fun parseDate(value: String): Long {
        val trimmed = value.trim()
        val zone = ZoneId.systemDefault()
        val dateTimePatterns = listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy年MM月dd日 HH:mm:ss",
        )
        dateTimePatterns.firstNotNullOfOrNull { pattern ->
            runCatching { LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern, Locale.CHINA)) }.getOrNull()
        }?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        val datePatterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyy年MM月dd日", "yyyy.MM.dd")
        val date = datePatterns.firstNotNullOfOrNull { pattern ->
            runCatching { LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(pattern, Locale.CHINA)) }.getOrNull()
        } ?: throw IllegalArgumentException("Invalid date")
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        val source = text.removePrefix("\uFEFF")
        while (index < source.length) {
            val character = source[index]
            when {
                character == '"' && quoted && source.getOrNull(index + 1) == '"' -> {
                    field.append('"'); index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    row += field.toString(); field.setLength(0)
                }
                (character == '\n' || character == '\r') && !quoted -> {
                    row += field.toString(); field.setLength(0); rows += row.toList(); row.clear()
                    if (character == '\r' && source.getOrNull(index + 1) == '\n') index++
                }
                else -> field.append(character)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString(); rows += row.toList()
        }
        require(!quoted) { "Unterminated CSV quote" }
        return rows
    }

    const val PREVIEW_LIMIT = 20

    /** 行打分低于此值即认为不是表头：单个「时间/金额」关键词不足以胜出（前导元数据行通常只命中 1 个字段）。 */
    private const val MIN_HEADER_MATCHES = 2

    /** 表头单元格可能带 UTF-8 BOM；用码点书写，避免源码里出现不可见字符（lint `ByteOrderMark`）。 */
    private val BOM_CHAR = 0xFEFF.toChar()
}
