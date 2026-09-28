package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate

/**
 * 三级回退管线装配（ARCHITECTURE §6 / `.trellis/spec/android/ai-ml.md`）：
 *
 * 1. **规则层**（[RuleEntryParser]，离线必备）产出草稿；
 * 2. **端侧分类器**（[CategoryClassifier]）只补规则层没定的分类；
 * 3. **云端 LLM**（[LlmParserClient]，可选）在规则层**产出不足**时补位，见 [needsCloud]（PRD D7）。
 *    开关关闭或实现缺失时 [cloud] 为 null，完全不发起网络调用（PRD D3）。
 *
 * 任何一级失败都不抛异常；结果一律 [ParseResult]。
 */
class NlEntryPipeline(
    accounts: List<Account> = emptyList(),
    categories: List<Category> = emptyList(),
    merchantRules: List<MerchantRule> = emptyList(),
    private val classifier: CategoryClassifier,
    private val cloud: LlmParserClient? = null,
) {

    private val accounts = accounts
    private val categories = categories
    private val ruleParser = RuleEntryParser(accounts, categories, merchantRules)

    suspend fun parse(text: String, today: LocalDate): ParseResult {
        val ruleResult = ruleParser.parse(text, today)
        val withClassifier = applyClassifier(ruleResult)

        // PRD D7：规则层（含分类器补位）已足够时绝不联网；不足时才尝试云端。
        if (!needsCloud(withClassifier)) return withClassifier

        // 未接云端（开关关闭 / 实现缺失）时直接返回规则层结果，不发起任何网络调用。
        val client = cloud ?: return withClassifier
        val context = ParseContext(today, accounts, categories)
        // 云端实现若违约抛异常，一律视同该级失败并回退规则层，绝不让异常逃逸到 UI
        // （`.trellis/spec/android/ai-ml.md`：「任何一级都不得抛出异常到 UI」）。
        val remote = try {
            client.parse(text, context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return withClassifier
        }
        val cloudEntries = when (remote) {
            is AppResult.Success -> remote.value.takeIf { it.isNotEmpty() } ?: return withClassifier
            is AppResult.Failure -> return withClassifier
        }
        // 规则层没有完整条目（如只识别到「昨天」没有金额）时，日期仍以规则层为准（PRD 硬约束 2）。
        val ruleDate = if (withClassifier.entries.isEmpty()) ruleParser.resolveDate(text, today) else null
        return merge(withClassifier, cloudEntries, ruleDate)
    }

    /**
     * PRD D7 触发判据：任一成立即认为规则层「产出不足」，云端补位——
     * (a) 零条目；(b) 有**云端能补的**字段落入 [ParsedEntry.unresolved]
     * （账户字段是豁免的，见 [CLOUD_CANNOT_RESOLVE_FIELDS]）；(c) 整体置信度非 [Confidence.HIGH]。
     *
     * 反例：LEARNED `merchant_rule` 命中且字段齐全的句子为 [Confidence.HIGH] 且无未识别字段，
     * 于是**不消耗云端调用**（PRD AC「同商户二次输入由规则层直接命中」）。
     */
    private fun needsCloud(result: ParseResult): Boolean {
        if (result.entries.isEmpty()) return true
        return result.entries.any { entry ->
            (entry.unresolved - CLOUD_CANNOT_RESOLVE_FIELDS).isNotEmpty() || entry.confidence != Confidence.HIGH
        }
    }

    private suspend fun applyClassifier(result: ParseResult): ParseResult {
        if (result.entries.isEmpty()) return result
        val updated = result.entries.map { entry ->
            if (entry.categoryId != null) {
                entry
            } else {
                val suggestion = classifySafely(entry).ranked().firstOrNull()
                if (suggestion == null) {
                    entry
                } else {
                    entry.copy(
                        categoryId = suggestion.categoryId,
                        unresolved = entry.unresolved - ParsedField.CATEGORY,
                    )
                }
            }
        }
        return toResult(updated, result.warnings)
    }

    /**
     * 分类器实现抛异常时视同「不出建议」降级，绝不让异常逃逸到 UI
     * （`.trellis/spec/android/ai-ml.md`）。`CancellationException` 原样抛出，不破坏协程取消。
     */
    private suspend fun classifySafely(entry: ParsedEntry): List<CategorySuggestion> =
        try {
            classifier.classify(entry.title, entry.remark)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }

    /**
     * 规则层结果与云端条目的合并（PRD 硬约束 2：语义取云端补位，金额/日期以规则层为准）：
     *
     * - 规则层一笔都没有 → 采用云端条目，但若规则层识别出日期则用它覆盖云端日期；
     * - 两边笔数一致 → 逐笔合并，规则层缺的字段（`unresolved`）才用云端补齐；
     * - 笔数对不上 → 不做合并，保留规则层结果，避免把云端条目错误分配到不同笔上。
     *
     * 多笔时**按金额唯一对齐**再合并（[alignByAmount]），而不是按位置：模型可能把多笔条目的顺序
     * 与原文颠倒，位置配对会把「打车」的分类/账户挂到「午饭」那笔上。金额无法唯一对齐
     * （重复金额或对不上）时退回位置配对，并只影响补位字段（金额/标题始终以规则层为准）。
     */
    private fun merge(rule: ParseResult, cloud: List<ParsedEntry>, ruleDate: LocalDate?): ParseResult {
        if (rule.entries.isEmpty()) {
            val entries = if (ruleDate == null) {
                cloud
            } else {
                cloud.map { it.copy(date = ruleDate, unresolved = it.unresolved - ParsedField.DATE) }
            }
            return toResult(entries, rule.warnings)
        }
        if (rule.entries.size != cloud.size) return rule
        val cloudOrdered = (if (rule.entries.size > 1) alignByAmount(rule.entries, cloud) else null) ?: cloud
        val merged = rule.entries.mapIndexed { index, entry -> mergeEntry(entry, cloudOrdered[index]) }
        return toResult(merged, rule.warnings)
    }

    /**
     * 用金额把云端条目重排成与规则层条目一一对应；仅当两边金额都唯一且能一一匹配时成立，
     * 否则返回 null（调用方退回位置配对）。金额是唯一的稳定主键——标题/分类正是要被补位的字段，
     * 不能拿它们做对齐依据。
     */
    private fun alignByAmount(rule: List<ParsedEntry>, cloud: List<ParsedEntry>): List<ParsedEntry>? {
        val byAmount = HashMap<Money, ParsedEntry>(cloud.size)
        for (entry in cloud) if (byAmount.put(entry.amount, entry) != null) return null
        val aligned = ArrayList<ParsedEntry>(rule.size)
        for (entry in rule) {
            aligned += byAmount.remove(entry.amount) ?: return null
        }
        return if (byAmount.isEmpty()) aligned else null
    }

    private fun mergeEntry(rule: ParsedEntry, cloud: ParsedEntry): ParsedEntry {
        var unresolved = rule.unresolved

        // 分类 / 账户 / 目标账户：规则层缺（unresolved）时才采纳云端；采纳后从 unresolved 移除。
        val categoryId = fillString(unresolved, ParsedField.CATEGORY, rule.categoryId, cloud.categoryId)
            .also { unresolved = it.second }.first
        val accountId = fillString(unresolved, ParsedField.ACCOUNT, rule.accountId, cloud.accountId)
            .also { unresolved = it.second }.first
        val toAccountId = fillString(unresolved, ParsedField.TO_ACCOUNT, rule.toAccountId, cloud.toAccountId)
            .also { unresolved = it.second }.first

        // 类型：规则层没线索（TYPE 未识别）且云端已确定时才采纳。
        val type = if (ParsedField.TYPE in unresolved && ParsedField.TYPE !in cloud.unresolved) {
            unresolved = unresolved - ParsedField.TYPE
            cloud.type
        } else {
            rule.type
        }

        // 日期：一律以规则层为准；仅当规则层也没识别出日期、且云端日期可信时才采纳。
        val date = if (ParsedField.DATE in unresolved && ParsedField.DATE !in cloud.unresolved) {
            unresolved = unresolved - ParsedField.DATE
            cloud.date
        } else {
            rule.date
        }

        // 标题：规则层标题为空时可被云端补全。
        val title = if (rule.title.isBlank() && cloud.title.isNotBlank()) {
            unresolved = unresolved - ParsedField.TITLE
            cloud.title
        } else {
            rule.title
        }

        return rule.copy(
            type = type,
            date = date,
            time = rule.time ?: cloud.time,
            accountId = accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
            title = title,
            remark = rule.remark.ifBlank { cloud.remark },
            confidence = stronger(rule.confidence, cloud.confidence),
            unresolved = unresolved,
            warnings = (rule.warnings + cloud.warnings).distinct(),
        )
    }

    private fun fillString(
        unresolved: Set<ParsedField>,
        field: ParsedField,
        ruleValue: String?,
        cloudValue: String?,
    ): Pair<String?, Set<ParsedField>> = when {
        ruleValue != null -> ruleValue to unresolved
        field in unresolved && cloudValue != null -> cloudValue to (unresolved - field)
        else -> ruleValue to unresolved
    }

    /** 取更强的置信度（HIGH 最强）：HIGH=0 < MEDIUM=1 < LOW=2，序数小者更强。 */
    private fun stronger(a: Confidence, b: Confidence): Confidence = if (a.ordinal <= b.ordinal) a else b

    private fun toResult(entries: List<ParsedEntry>, carryWarnings: List<NlpWarning> = emptyList()): ParseResult {
        val unresolved = entries.flatMapTo(mutableSetOf()) { it.unresolved }
        val warnings = (carryWarnings + entries.flatMap { it.warnings }).distinct()
        return if (unresolved.isEmpty()) {
            ParseResult.Ok(entries)
        } else {
            ParseResult.Partial(entries, unresolved, warnings)
        }
    }

    private companion object {
        /**
         * 云端**造不出来**的「未识别」字段——因此它们不构成 [needsCloud] 的触发条件。
         *
         * 账户 / 目标账户只能是用户库里已存在、且由用户在本页手选的 id：云端看不到用户的账户表，
         * 凭空给不出一个合法 id（实测 `gemma3:4b` 给的账户就是不存在的，会被 id 后校验挡成「未识别」，
         * 见 PRD Requirement 8 禁止静默降级成默认账户）。而用户的账户名（如「现金」「card」）
         * 几乎不会出现在账目文本里——若把 ACCOUNT 计入触发条件，等于**几乎每句都联网**
         * （实测单次 15–60 s），直接违背 PRD D7「规则层产出不足时才补位」与 AC
         * 「同商户二次输入由规则层直接命中、不消耗云端调用」。
         *
         * 注意这只是**云端触发**判据的豁免：[ParsedEntry.unresolved] 里仍保留 ACCOUNT，
         * 界面照常显示「未能识别：账户」并要求用户选择后才能确认（Requirement 8）。
         *
         * 不要「简化」回「只看 unresolved 是否为空」：`TO_ACCOUNT` 与 `ACCOUNT` 同类
         * （转账收款方也是云端造不出来的 id），必须一起排除。
         */
        private val CLOUD_CANNOT_RESOLVE_FIELDS = setOf(ParsedField.ACCOUNT, ParsedField.TO_ACCOUNT)
    }
}
