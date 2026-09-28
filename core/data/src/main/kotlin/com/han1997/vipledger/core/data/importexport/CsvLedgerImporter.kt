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
        val normalized = headers.map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        fun find(vararg candidates: String): Int? =
            normalized.indexOfFirst { header -> candidates.any(header::contains) }.takeIf { it >= 0 }
        val time = find("交易时间", "发生时间", "创建时间", "时间", "日期", "date")
        val amount = find("金额", "金额(元)", "金额（元）", "amount")
        val type = find("收/支", "收支", "交易类型", "类型", "收付款")
        val title = find("商品", "商品名称", "交易对方", "商户", "摘要", "标题", "商品说明", "name")
        val remark = find("备注", "描述", "remark")
        val account = find("支付方式", "收/付款方式", "付款方式", "账户")
        return CsvMapping(
            buildMap {
                time?.let { put(CsvField.TIME, it) }
                amount?.let { put(CsvField.AMOUNT, it) }
                type?.let { put(CsvField.TYPE, it) }
                title?.let { put(CsvField.TITLE, it) }
                remark?.let { put(CsvField.REMARK, it) }
                account?.let { put(CsvField.ACCOUNT, it) }
            },
        )
    }

    fun fingerprint(occurredAt: Long, amountCents: Long, accountId: String, title: String): String {
        val source = "${occurredAt / 60_000}|$amountCents|$accountId|$title".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-1").digest(source).joinToString("") { "%02x".format(it) }
    }

    private fun dataRows(records: List<List<String>>, headerIndex: Int): List<List<String>> =
        records.drop(headerIndex + 1).filter { row -> row.any(String::isNotBlank) }

    /** 表头行探测：预览与解析共用同一套判据，避免两处结果不一致。 */
    private fun headerIndex(records: List<List<String>>): Int =
        records.indexOfFirst { row ->
            row.any {
                it.contains("时间") || it.contains("日期") || it.contains("交易时间") || it.equals("date", true)
            }
        }.coerceAtLeast(0)

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
}
