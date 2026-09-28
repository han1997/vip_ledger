package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.model.TransactionType
import kotlinx.datetime.LocalDate

/**
 * 规则层（tier 1）入口：纯函数、**不读时钟、不读库**。
 *
 * 参考日 [today] 与账户/分类/规则候选全部作为入参，因此相对日期（「上周三」）可在纯 JVM 单测里锁死。
 *
 * 架构（研究 §4）：Span 表 + 优先级认领，固定遍次
 * `P0 归一化 → 日期 → 时间 → 比例 → 金额 → 账户 → merchant_rule → 类型 → 分类 → 组装`。
 * **绝不做顺序消费文本**——日期/比例先占位，金额只能看到掩码后的文本。
 */
class RuleEntryParser(
    private val accounts: List<Account> = emptyList(),
    private val categories: List<Category> = emptyList(),
    private val merchantRules: List<MerchantRule> = emptyList(),
) {

    /** 解析一句话，最多产出多笔（PRD Requirement 6）。永不抛异常。 */
    fun parse(text: String, today: LocalDate): ParseResult = runCatching {
        parseInternal(text, today)
    }.getOrElse { ParseResult.Failed(ParseFailure.INTERNAL_ERROR) }

    /**
     * 只解析日期，不抽金额/账户。供管线在规则层没有完整条目（如只说了「昨天」没金额）时，
     * 仍能按「日期以规则层为准」覆盖云端给出的日期（PRD 硬约束 2）。
     *
     * 未识别到日期返回 null，调用方应保留云端日期并保留其 [ParsedField.DATE] 未识别标记。
     */
    fun resolveDate(text: String, today: LocalDate): LocalDate? {
        val normalized = NlNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        return DateExtractor.extract(normalized, SpanTable(normalized), today)
    }

    private fun parseInternal(text: String, today: LocalDate): ParseResult {
        val normalized = NlNormalizer.normalize(text)
        if (normalized.isBlank()) return ParseResult.Failed(ParseFailure.EMPTY_INPUT)

        val table = SpanTable(normalized)
        val date = DateExtractor.extract(normalized, table, today) ?: today
        val time = TimeExtractor.extract(normalized, table)
        val ratioFound = RatioExtractor.extract(normalized, table)

        val amounts = AmountExtractor.extract(normalized, table)
        val accountMatches = AccountMatcher.match(normalized, table, accounts)

        val residual = clean(table.masked())
        val typeResult = TypeClassifier.classify(normalized, accountMatches, accounts)
        val ruleHit = MerchantRuleMatcher.firstHit(residual, "", merchantRules)
        val categoryId = ruleHit?.categoryId ?: CategoryMatcher.match(residual, categories)

        val baseWarnings = buildList {
            addAll(typeResult.warnings)
            if (ratioFound) add(NlpWarning.RATIO_NOT_RESOLVED)
            // 有单位候选优先时被丢弃的裸金额必须显式提示，不能静默少一笔（Requirement 8 精神）。
            if (amounts.droppedBareCount > 0) add(NlpWarning.BARE_AMOUNT_DROPPED)
        }

        val orderedAmounts = amounts.candidates.sortedBy { it.span.start }
        if (orderedAmounts.isEmpty()) {
            return if (table.spans.isNotEmpty()) {
                ParseResult.Partial(
                    entries = emptyList(),
                    unresolved = setOf(ParsedField.AMOUNT),
                    warnings = baseWarnings,
                )
            } else {
                ParseResult.Failed(ParseFailure.NO_AMOUNT)
            }
        }

        val masked = table.masked()
        val titles = titlesFor(orderedAmounts, masked, residual)
        val accountPair = resolveAccounts(typeResult.type, accountMatches, accounts, normalized)

        val entries = orderedAmounts.mapIndexed { index, candidate ->
            val title = titles[index]
            val entryWarnings = baseWarnings.toMutableList()

            val unresolved = buildSet {
                if (!typeResult.hasCue) add(ParsedField.TYPE)
                if (accountPair.accountId == null) add(ParsedField.ACCOUNT)
                if (typeResult.type.requiresTargetAccount && accountPair.toAccountId == null) {
                    add(ParsedField.TO_ACCOUNT)
                    entryWarnings += NlpWarning.MISSING_TARGET_ACCOUNT
                }
                if (categoryId == null) add(ParsedField.CATEGORY)
                if (title.isBlank()) add(ParsedField.TITLE)
            }

            ParsedEntry(
                amount = candidate.value,
                type = typeResult.type,
                date = date,
                time = time,
                accountId = accountPair.accountId,
                toAccountId = accountPair.toAccountId,
                categoryId = categoryId,
                title = title,
                remark = "",
                confidence = confidenceFor(candidate, typeResult, entryWarnings, ruleHit != null),
                amountHasUnit = candidate.hasUnit,
                matchedRuleId = ruleHit?.ruleId,
                unresolved = unresolved,
                warnings = entryWarnings.distinct(),
                consumed = table.spans,
            )
        }

        return if (entries.all { it.unresolved.isEmpty() }) {
            ParseResult.Ok(entries)
        } else {
            ParseResult.Partial(
                entries = entries,
                unresolved = entries.flatMapTo(mutableSetOf()) { it.unresolved },
                warnings = entries.flatMap { it.warnings }.distinct(),
            )
        }
    }

    private data class AccountPair(val accountId: String?, val toAccountId: String?)

    private fun resolveAccounts(
        type: TransactionType,
        matches: List<AccountMatcher.Match>,
        accounts: List<Account>,
        text: String,
    ): AccountPair {
        if (matches.isEmpty()) return AccountPair(null, null)
        return when (type) {
            TransactionType.TRANSFER -> resolveTransfer(matches, text)
            TransactionType.DEBT_REPAY -> {
                val debt = matches.firstOrNull { match ->
                    accounts.firstOrNull { it.id == match.accountId }?.isDebt == true
                }
                val nonDebt = matches.firstOrNull { it != debt }
                AccountPair(nonDebt?.accountId, debt?.accountId)
            }
            else -> AccountPair(matches.first().accountId, null)
        }
    }

    /** 转账：`到/给` 紧邻的账户是收款方（toAccount），其余为转出方。 */
    private fun resolveTransfer(matches: List<AccountMatcher.Match>, text: String): AccountPair {
        val sorted = matches.sortedBy { it.span.start }
        val target = sorted.firstOrNull { hasTargetMarker(text, it.span.start) }
        if (target != null) {
            val from = sorted.firstOrNull { it != target }
            return AccountPair(from?.accountId, target.accountId)
        }
        if (sorted.size == 1) return AccountPair(sorted[0].accountId, null)
        return AccountPair(sorted.first().accountId, sorted.last().accountId)
    }

    private fun hasTargetMarker(text: String, index: Int): Boolean {
        for (offset in 1..2) {
            val at = index - offset
            if (at < 0) break
            when (text[at]) {
                '到', '给' -> return true
            }
        }
        return false
    }

    private fun titlesFor(
        amounts: List<AmountCandidate>,
        masked: String,
        residual: String,
    ): List<String> {
        if (amounts.size == 1) return listOf(residual)
        return amounts.mapIndexed { index, candidate ->
            val from = if (index == 0) 0 else amounts[index - 1].span.end
            val segment = if (from <= candidate.span.start && candidate.span.start <= masked.length) {
                masked.substring(from, candidate.span.start)
            } else {
                ""
            }
            clean(segment).ifEmpty { residual }
        }
    }

    private fun confidenceFor(
        candidate: AmountCandidate,
        typeResult: TypeClassifier.Result,
        warnings: List<NlpWarning>,
        ruleHit: Boolean,
    ): Confidence {
        if (warnings.contains(NlpWarning.AMBIGUOUS_TYPE_CUE)) return Confidence.LOW
        // merchant_rule 命中即定分类（研究 §3.3 证据 +4），分类不再是未知量 → 整体 HIGH。
        // 这样「同商户二次输入」在规则层即可判为 HIGH，不再触发云端（PRD D7 / AC）。
        if (ruleHit) return Confidence.HIGH
        return when {
            candidate.hasUnit && typeResult.hasCue -> Confidence.HIGH
            candidate.hasUnit || typeResult.hasCue -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    /** 把掩码字符还原成空格并压缩空白，得到「标题/备注」用文本。 */
    private fun clean(text: String): String =
        text.replace('\u0000', ' ').split(' ').filter { it.isNotBlank() }.joinToString(" ").trim()

    companion object {
        /** 无账户/分类/规则候选的默认实例，便于测试与简单调用。 */
        fun empty(): RuleEntryParser = RuleEntryParser()
    }
}

/** 便捷入口：用默认空候选解析（不做账户/分类/规则匹配）。 */
fun parseNlEntry(text: String, today: LocalDate): ParseResult = RuleEntryParser.empty().parse(text, today)
