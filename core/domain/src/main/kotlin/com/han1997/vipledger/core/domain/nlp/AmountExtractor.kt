package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.model.money.Money

/** 金额候选的来源形态，决定证据强度与筛选规则。 */
enum class AmountKind {
    /** 带货币单位/符号：`35 块`、`¥35`、`十二块五`。 */
    CURRENCY,

    /** 纯阿拉伯数字，无单位：`35`、`28.5`。 */
    ARABIC,

    /** 纯中文数词，无单位：`一百二`。 */
    CHINESE,
}

data class AmountCandidate(
    val value: Money,
    val span: Span,
    val kind: AmountKind,
) {
    val hasUnit: Boolean get() = kind == AmountKind.CURRENCY
}

/**
 * P4 金额抽取的产出：认领后的候选 + 被启发式丢弃的裸数字候选数量。
 *
 * [droppedBareCount] > 0 时上层应发 [NlpWarning.BARE_AMOUNT_DROPPED]，避免「有单位优先」的
 * 筛选静默吞掉同句里的裸金额（PRD Requirement 8 精神）。
 */
data class AmountExtraction(
    val candidates: List<AmountCandidate>,
    val droppedBareCount: Int = 0,
)

/**
 * P4 金额抽取：在已被更高优先级 span 掩码后的文本上扫描候选。
 *
 * 不产生「顺序消费」——只返回候选并尝试认领；多个候选即对应「一句话多笔」（PRD Requirement 6）。
 * 筛选规则（研究 §1.3）：带单位/阿拉伯数字的候选全部保留；纯中文数字候选只在没有其它候选时
 * 保留最大的一个，避免把 `三斤` 这类量词误当金额。
 * 「连续 ≥ 7 位数字」不认成金额（手机号/卡号保护）。
 */
object AmountExtractor {

    private const val YUAN_UNITS = "元块圆员"
    private const val JIAO_UNITS = "角毛"
    private const val CURRENCY_SYMBOLS = "¥￥$"
    private const val CN_DIGITS = "零〇一二两俩三四五六七八九"
    private const val CN_UNITS = "十百千万亿"
    private const val MAX_BARE_DIGITS = 6

    fun extract(text: String, table: SpanTable): AmountExtraction {
        val masked = table.masked()
        val candidates = ArrayList<AmountCandidate>()
        var i = 0
        while (i < masked.length) {
            val c = masked[i]
            if (c == '\u0000' || c == ' ') {
                i++
                continue
            }
            val match = matchAt(text, i)
            if (match != null) {
                val money = Money.parse(AmountNormalizer.normalize(match.raw) ?: "")
                if (money != null && !money.isZero) {
                    val span = Span(i, match.end, SpanKind.AMOUNT)
                    if (table.tryClaim(span)) {
                        candidates += AmountCandidate(money, span, match.kind)
                    }
                }
                i = match.end
            } else {
                i++
            }
        }
        return select(candidates)
    }

    private data class RawMatch(val end: Int, val raw: String, val kind: AmountKind)

    private fun matchAt(text: String, start: Int): RawMatch? {
        var pos = start
        var hasSymbol = false
        if (pos < text.length && text[pos] in CURRENCY_SYMBOLS) {
            hasSymbol = true
            pos++
        }

        val numberStart = pos
        val arabic = pos < text.length && text[pos].isDigit()
        if (arabic) {
            val digitStart = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == ',')) pos++
            val intDigits = text.substring(digitStart, pos).count { it.isDigit() }
            if (pos < text.length && text[pos] == '.') {
                pos++
                while (pos < text.length && text[pos].isDigit()) pos++
            }
            if (intDigits > MAX_BARE_DIGITS) return null
            // 大数乘数：3000万 / 1.5亿
            if (pos < text.length && (text[pos] == '万' || text[pos] == '亿')) pos++
        } else if (pos < text.length && isCnNumeral(text[pos])) {
            while (pos < text.length && isCnNumeral(text[pos])) pos++
        } else {
            return null
        }
        val numberEnd = pos

        val suffix = readSuffix(text, numberEnd)
        val end = suffix.end
        // 只有符号、没有数字的输入（如孤立的 `¥`）不是金额。
        if (numberEnd == numberStart && !arabic) return null

        val raw = text.substring(numberStart, end)
        val kind = when {
            hasSymbol || suffix.hasUnit -> AmountKind.CURRENCY
            arabic -> AmountKind.ARABIC
            else -> AmountKind.CHINESE
        }
        return RawMatch(end, raw, kind)
    }

    private data class SuffixResult(val end: Int, val hasUnit: Boolean)

    /**
     * 读取数字之后的量词链。
     * - 允许数字与单位之间有一个空格（`35 块`）；
     * - `元/块/角/毛/分` 之后的裸数字只在**紧邻**时吞入（`12块5`），避免吞掉下一段文字。
     */
    private fun readSuffix(text: String, from: Int): SuffixResult {
        var pos = from
        var hasUnit = false
        var spaced = 0
        while (pos < text.length && text[pos] == ' ') {
            pos++
            spaced++
        }
        if (pos >= text.length) return SuffixResult(from, false)

        val c = text[pos]
        when {
            c in YUAN_UNITS -> {
                hasUnit = true
                pos++
                pos = readSubUnits(text, pos)
            }
            c in JIAO_UNITS -> {
                hasUnit = true
                pos++
                pos = readFraction(text, pos)
            }
            c == '分' -> {
                hasUnit = true
                pos++
            }
            else -> return SuffixResult(from, false)
        }
        return SuffixResult(pos, hasUnit)
    }

    /** `元/块` 之后：紧邻的裸数字/角分链（`12块5`、`12元5角3分`、`12块零5`）。 */
    private fun readSubUnits(text: String, from: Int): Int {
        var pos = from
        if (pos < text.length && text[pos] == '零') {
            pos++
            pos = consumeDigits(text, pos)
            if (pos < text.length && text[pos] == '分') pos++
            return pos
        }
        if (pos < text.length && isDigitAny(text[pos])) {
            pos = consumeDigits(text, pos)
            if (pos < text.length && text[pos] in JIAO_UNITS) {
                pos++
                pos = readFraction(text, pos)
            } else if (pos < text.length && text[pos] == '分') {
                pos++
            }
            return pos
        }
        // 允许「12元 5角」这样的空格分隔显式单位
        var probe = pos
        while (probe < text.length && text[probe] == ' ') probe++
        if (probe > pos && probe < text.length && (isDigitAny(text[probe]))) {
            var q = consumeDigits(text, probe)
            if (q < text.length && text[q] in JIAO_UNITS) {
                q++
                q = readFraction(text, q)
                return q
            }
            if (q < text.length && text[q] == '分') return q + 1
        }
        return pos
    }

    /** 角/毛之后的分位（`...5角3分`）。 */
    private fun readFraction(text: String, from: Int): Int {
        var pos = from
        while (pos < text.length && text[pos] == ' ') pos++
        if (pos < text.length && isDigitAny(text[pos])) {
            pos = consumeDigits(text, pos)
            if (pos < text.length && text[pos] == '分') pos++
        }
        return pos
    }

    private fun consumeDigits(text: String, from: Int): Int {
        var pos = from
        while (pos < text.length && isDigitAny(text[pos])) pos++
        return pos
    }

    private fun isDigitAny(c: Char): Boolean = c.isDigit() || c in CN_DIGITS

    private fun isCnNumeral(c: Char): Boolean = c in CN_DIGITS || c in CN_UNITS || c == '点'

    private fun select(candidates: List<AmountCandidate>): AmountExtraction {
        // 带货币单位者全部保留（支持一句话多笔）；有单位时不再保留无单位的数字。
        val currency = candidates.filter { it.kind == AmountKind.CURRENCY }
        if (currency.isNotEmpty()) return AmountExtraction(currency, candidates.size - currency.size)
        val arabic = candidates.filter { it.kind == AmountKind.ARABIC }
        if (arabic.isNotEmpty()) return AmountExtraction(arabic, candidates.size - arabic.size)
        // 纯中文数字候选噪声大（`一样` 的 `一`、量词 `三斤` 的 `三`）；只保留 ≥10 元的最大者。
        val kept = candidates
            .filter { it.value.cents >= MIN_BARE_CHINESE_CENTS }
            .maxByOrNull { it.value.cents }
            ?.let { listOf(it) }
            ?: emptyList()
        return AmountExtraction(kept, candidates.size - kept.size)
    }

    /** 裸中文数字（无货币单位）低于 10 元一律不认，避免把量词/副词当成金额。 */
    private const val MIN_BARE_CHINESE_CENTS = 1000L
}
