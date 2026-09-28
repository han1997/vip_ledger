package com.han1997.vipledger.core.domain.nlp

/**
 * P3 比例抽取：`折/折扣/百分之/成` 一律认领为 RATIO，**不产生金额**（研究 §1.3）。
 *
 * 优先级高于金额，因此「打八折 后 240」里 `八折` 被吃掉，金额只剩 240；
 * 「五折」单独出现时整句无金额（折扣价需原价，规则层做不到，标 known-miss）。
 */
object RatioExtractor {

    fun extract(text: String, table: SpanTable): Boolean {
        var found = false
        for (regex in PATTERNS) {
            regex.findAll(text).forEach { match ->
                if (table.tryClaim(match.span())) found = true
            }
        }
        return found
    }

    private fun MatchResult.span() = Span(range.first, range.last + 1, SpanKind.RATIO)

    private val CN_NUM = "[零〇一二两俩三四五六七八九十百千万亿\\d]+"

    private val PATTERNS = listOf(
        Regex("打?${CN_NUM}折(扣)?"),
        Regex("百分之${CN_NUM}"),
        Regex("${CN_NUM}成"),
    )
}
