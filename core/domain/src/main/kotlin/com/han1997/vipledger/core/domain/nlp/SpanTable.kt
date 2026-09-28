package com.han1997.vipledger.core.domain.nlp

/**
 * span 的语义类别。**枚举声明顺序即认领优先级**（高 → 低）：
 * DATE/TIME ＞ RATIO（折/百分之）＞ AMOUNT ＞ ACCOUNT/MERCHANT ＞ CUE。
 *
 * 规则层绝不做「顺序消费文本」；先把所有候选抽成 span，再按本顺序落表，
 * 这样「3月5日」里的数字不可能被当成金额，「五折」也不会被当金额。
 * 依据：`.trellis/tasks/09-24-m6-ai-entry/research/chinese-nl-rule-parser.md` §4.1。
 */
enum class SpanKind {
    DATE,
    TIME,
    RATIO,
    AMOUNT,
    ACCOUNT,
    MERCHANT,
    CUE,
}

/** 归一化文本上的半开区间 `[start, end)`。 */
data class Span(val start: Int, val end: Int, val kind: SpanKind) {
    val length: Int get() = end - start

    fun overlaps(other: Span): Boolean = start < other.end && other.start < end
}

/**
 * 区间表：高优先级先落，低优先级遇到重叠只能退让（放弃整个候选，不做缩小）。
 *
 * [masked] 把已占字符替换成空格，保持下标不变，后续抽取器可继续用全局偏移定位。
 */
class SpanTable(private val text: String) {

    private val taken = ArrayList<Span>()

    /** 已认领的 span，按认领顺序（即优先级顺序）返回。 */
    val spans: List<Span> get() = taken.toList()

    /** 尝试认领 [span]；与已占区间重叠时返回 false 且不改动表。 */
    fun tryClaim(span: Span): Boolean {
        if (span.end <= span.start) return false
        if (taken.any { it.overlaps(span) }) return false
        taken += span
        return true
    }

    /** `[start, end)` 是否与任一已占区间重叠。 */
    fun isTaken(start: Int, end: Int): Boolean =
        taken.any { it.start < end && start < it.end }

    fun masked(): String {
        val chars = text.toCharArray()
        for (span in taken) {
            for (i in span.start until span.end) {
                if (i in chars.indices) chars[i] = MASK_CHAR
            }
        }
        return String(chars)
    }

    private companion object {
        /** 用 NUL 而非空格做掩码字符，避免与原始空格混淆（归一化文本里空格仅作分隔）。 */
        const val MASK_CHAR: Char = '\u0000'
    }
}
