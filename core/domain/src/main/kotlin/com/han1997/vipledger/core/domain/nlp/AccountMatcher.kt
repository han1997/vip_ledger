package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.domain.model.Account

/**
 * P5 账户匹配：只在调用方传入的真实账户名上做匹配，**产出 id 候选而非自由文本**
 * （PRD「账户/分类输出为真实名称匹配后的 id 候选」）。
 *
 * 名称按长度降序尝试，`EQUALS` 优先于 `CONTAINS`；命中即认领 ACCOUNT span。
 */
object AccountMatcher {

    data class Match(val accountId: String, val name: String, val span: Span)

    fun match(text: String, table: SpanTable, accounts: List<Account>, kind: SpanKind = SpanKind.ACCOUNT): List<Match> {
        val masked = table.masked()
        val result = ArrayList<Match>()
        val candidates = accounts
            .filter { it.name.isNotBlank() && !it.isArchived }
            .sortedWith(compareByDescending<Account> { it.name.length }.thenBy { it.name })

        for (account in candidates) {
            var from = 0
            while (from <= masked.length - account.name.length) {
                val index = masked.indexOf(account.name, from)
                if (index < 0) break
                val end = index + account.name.length
                if (!table.isTaken(index, end)) {
                    val span = Span(index, end, kind)
                    if (table.tryClaim(span)) {
                        result += Match(account.id, account.name, span)
                    }
                }
                from = index + account.name.length
            }
        }
        return result.sortedBy { it.span.start }
    }
}
