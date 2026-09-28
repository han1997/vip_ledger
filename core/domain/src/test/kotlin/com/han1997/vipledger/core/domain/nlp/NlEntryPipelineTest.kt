package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * 三级管线装配（规则 → 分类器 → 云端）的行为、阈值/top-N 口径，以及 PRD D7 的云端触发判据与合并。
 *
 * 分类器用测试内的假实现，**不依赖 `:ml:classifier`**（避免 core 反向依赖 ml）。
 * 占位实现自身的行为在 `:ml:classifier` 的 `src/test` 里验证。
 */
class NlEntryPipelineTest {

    private class FixedClassifier(private val candidates: List<CategorySuggestion>) : CategoryClassifier {
        override suspend fun classify(title: String, remark: String): List<CategorySuggestion> = candidates
    }

    private class FakeCloud(private val response: AppResult<List<ParsedEntry>>) : LlmParserClient {
        var callCount = 0
        var lastContext: ParseContext? = null

        override suspend fun parse(text: String, context: ParseContext): AppResult<List<ParsedEntry>> {
            callCount++
            lastContext = context
            return response
        }
    }

    /** 违约实现：抛异常而非返回降级值，用来验证管线不会把异常泄漏给调用方。 */
    private class ThrowingClassifier : CategoryClassifier {
        override suspend fun classify(title: String, remark: String): List<CategorySuggestion> =
            throw IllegalStateException("classifier boom")
    }

    private class ThrowingCloud : LlmParserClient {
        override suspend fun parse(text: String, context: ParseContext): AppResult<List<ParsedEntry>> =
            throw IllegalStateException("cloud boom")
    }

    private fun pipeline(
        classifier: CategoryClassifier,
        cloud: LlmParserClient? = null,
        rules: List<MerchantRule> = emptyList(),
    ) = NlEntryPipeline(
        accounts = CORPUS_ACCOUNTS,
        categories = CORPUS_CATEGORIES,
        merchantRules = rules,
        classifier = classifier,
        cloud = cloud,
    )

    @Test
    fun classifierFillsCategoryThatRulesCouldNotDetermine() = runTest {
        val result = pipeline(FixedClassifier(listOf(CategorySuggestion("cat_food", 0.9f))))
            .parse("昨天午饭 35 块 微信", REF)
        val entry = result.entries.single()
        assertEquals("cat_food", entry.categoryId)
        assertTrue(!entry.unresolved.contains(ParsedField.CATEGORY))
    }

    @Test
    fun belowThresholdCandidateYieldsNoSuggestion() = runTest {
        val result = pipeline(FixedClassifier(listOf(CategorySuggestion("cat_food", 0.2f))))
            .parse("昨天午饭 35 块 微信", REF)
        val entry = result.entries.single()
        assertNull(entry.categoryId, "低于阈值不应出建议")
        assertTrue(entry.unresolved.contains(ParsedField.CATEGORY))
    }

    @Test
    fun placeholderClassifierReturnsNoCandidates() = runTest {
        // 模拟占位实现：空候选列表 → 规则层结果不受影响。
        val result = pipeline(FixedClassifier(emptyList())).parse("昨天午饭 35 块 微信", REF)
        assertNull(result.entries.single().categoryId)
    }

    // ---- PRD D7：云端触发判据 ----

    @Test
    fun fullyResolvedHighConfidenceRuleHitDoesNotCallCloud() = runTest {
        // LEARNED merchant_rule 命中 + 类型/账户/分类/标题齐全 → HIGH 且无未识别字段 → 不联网。
        val cloud = FakeCloud(AppResult.success(listOf(cloudEntry())))
        val rules = listOf(merchantRule("rule_refund", "退款", "cat_refund", source = RuleSource.LEARNED))
        val result = pipeline(FixedClassifier(emptyList()), cloud, rules).parse("收到退款 88 元 微信", REF)
        assertEquals(0, cloud.callCount, "规则层已产出完整 HIGH 结果时不得调用云端")
        val entry = result.entries.single()
        assertEquals(Confidence.HIGH, entry.confidence)
        assertEquals("cat_refund", entry.categoryId)
        assertEquals("rule_refund", entry.matchedRuleId)
    }

    @Test
    fun unresolvedFieldTriggersCloudEvenAtHighConfidence() = runTest {
        // 规则命中使分类确定并升为 HIGH，但类型仍缺（账户字段已豁免，不参与触发）→
        // 仍应触发云端补位（D7 条件 b 对「云端能补的字段」成立）。
        // 云端返回失败，使条目不被合并，从而能直接观察规则层真实输出。
        val cloud = FakeCloud(AppResult.Failure(AppError.Network("offline")))
        val rules = listOf(merchantRule("rule_lunch", "午饭", "cat_food"))
        val result = pipeline(FixedClassifier(emptyList()), cloud, rules).parse("午饭 35 块", REF)
        val entry = result.entries.single()
        assertTrue(entry.unresolved.contains(ParsedField.TYPE), "类型无线索，应标未识别")
        assertEquals(Confidence.HIGH, entry.confidence, "规则命中即 HIGH")
        assertEquals(1, cloud.callCount, "存在云端能补的未识别字段（类型）时应调用云端")
    }

    @Test
    fun unresolvedAccountAloneDoesNotTriggerCloud() = runTest {
        // 设备实测场景：LEARNED 规则已定分类、类型由线索词（收到/退款）确定、金额带单位 → HIGH，
        // 唯一未识别的是账户（句中没提账户名）。账户云端造不出来、必须由用户手选 → 不得联网。
        // 改动前此用例会联网（callCount == 1），改动后必须为 0。
        val cloud = FakeCloud(AppResult.success(listOf(cloudEntry())))
        val rules = listOf(merchantRule("rule_refund", "退款", "cat_refund", source = RuleSource.LEARNED))
        val result = pipeline(FixedClassifier(emptyList()), cloud, rules).parse("收到退款 88 元", REF)
        assertEquals(0, cloud.callCount, "账户未识别不得触发云端（PRD D7 豁免账户字段）")
        val entry = result.entries.single()
        assertEquals(setOf(ParsedField.ACCOUNT), entry.unresolved, "账户仍应标未识别，由用户选择")
        assertEquals(Confidence.HIGH, entry.confidence)
        assertEquals("cat_refund", entry.categoryId)
        assertEquals("rule_refund", entry.matchedRuleId)
    }

    @Test
    fun unresolvedTargetAccountOnTransferDoesNotTriggerCloud() = runTest {
        // 转账收款方（TO_ACCOUNT）与账户同类：云端造不出合法 id → 同样不得触发云端。
        val cloud = FakeCloud(AppResult.success(listOf(cloudEntry())))
        val rules = listOf(merchantRule("rule_transfer", "转账", "cat_rent", source = RuleSource.LEARNED))
        val result = pipeline(FixedClassifier(emptyList()), cloud, rules).parse("从微信转账 500 元", REF)
        assertEquals(0, cloud.callCount, "收款账户未识别不得触发云端（PRD D7 豁免账户字段）")
        val entry = result.entries.single()
        assertEquals(TransactionType.TRANSFER, entry.type)
        assertEquals("acct_wechat", entry.accountId, "转出账户已识别")
        assertEquals(setOf(ParsedField.TO_ACCOUNT), entry.unresolved, "收款账户仍应标未识别")
        assertEquals(Confidence.HIGH, entry.confidence)
    }

    @Test
    fun unresolvedCategoryStillTriggersCloud() = runTest {
        // 分类是云端**能**补的字段（调用方传入真实分类列表后校验）→ 分类未识别仍触发云端。
        // 回归护栏：豁免只为账户字段，且不得把「未识别即触发」整体削弱。
        val cloud = FakeCloud(AppResult.Failure(AppError.Network("offline")))
        val result = pipeline(FixedClassifier(emptyList()), cloud).parse("收到奖金 12000 元 微信", REF)
        val entry = result.entries.single()
        assertTrue(entry.unresolved.contains(ParsedField.CATEGORY), "句中无分类线索")
        assertEquals(Confidence.HIGH, entry.confidence)
        assertEquals(1, cloud.callCount, "分类未识别应触发云端")
    }

    @Test
    fun mediumConfidenceWithAllFieldsResolvedStillTriggersCloud() = runTest {
        // 所有字段都定了，但置信度非 HIGH（金额无单位）→ 仍触发云端（D7 条件 c）。
        val cloud = FakeCloud(AppResult.success(listOf(cloudEntry())))
        val result = pipeline(FixedClassifier(emptyList()), cloud).parse("收到工资 12000 微信", REF)
        assertEquals(1, cloud.callCount, "置信度非 HIGH 时应调用云端")
        assertTrue(result.entries.single().unresolved.isEmpty())
    }

    @Test
    fun cloudNotCalledWhenSwitchOff() = runTest {
        // 联网开关关闭 = cloud 为 null；规则层结果原样返回，不发起网络调用。
        val result = pipeline(FixedClassifier(emptyList()), cloud = null).parse("昨天午饭 35 块", REF)
        val entry = result.entries.single()
        assertNull(entry.categoryId)
        assertTrue(entry.unresolved.contains(ParsedField.CATEGORY))
    }

    // ---- PRD 硬约束 2 + Requirement 8：合并语义 ----

    @Test
    fun cloudFillsSemanticsButRuleAmountAndDateWin() = runTest {
        val cloud = FakeCloud(
            AppResult.success(listOf(cloudEntry(amountCents = 4200, date = REF, categoryId = "cat_food", accountId = "acct_wechat"))),
        )
        val result = pipeline(FixedClassifier(emptyList()), cloud).parse("昨天午饭 35 块", REF)
        assertEquals(1, cloud.callCount)
        val entry = result.entries.single()
        assertEquals(Money.ofCents(3500), entry.amount, "金额以规则层为准，不用云端值")
        assertEquals(LocalDate(2026, 9, 27), entry.date, "日期以规则层为准（硬约束 2）")
        assertEquals("cat_food", entry.categoryId, "分类可用云端补位")
        assertEquals("acct_wechat", entry.accountId, "账户可用云端补位")
    }

    @Test
    fun ruleDateOverridesUntrustedCloudDateWhenRulesProduceNoEntry() = runTest {
        // 只说了「昨天」没金额 → 规则层无条目；云端日期不可信（DATE 未识别）时用规则层日期。
        val cloud = FakeCloud(
            AppResult.success(listOf(cloudEntry(date = REF, unresolved = setOf(ParsedField.DATE)))),
        )
        val result = pipeline(FixedClassifier(emptyList()), cloud).parse("昨天", REF)
        assertEquals(LocalDate(2026, 9, 27), result.entries.single().date)
    }

    @Test
    fun multiEntryMergeAlignsByAmountNotPosition() = runTest {
        // 模型把两笔顺序颠倒（打车在前）时，不得把分类错配到另一笔上（Requirement 6）。
        val cloud = FakeCloud(
            AppResult.success(
                listOf(
                    cloudEntry(amountCents = 2800, categoryId = "cat_transport", title = "打车"),
                    cloudEntry(amountCents = 3500, categoryId = "cat_food", title = "午饭"),
                ),
            ),
        )
        val result = pipeline(FixedClassifier(emptyList()), cloud).parse("午饭35块 打车28块", REF)

        val byAmount = result.entries.associateBy { it.amount }
        assertEquals("cat_food", byAmount[Money.ofCents(3500)]?.categoryId, "35 元的分类应挂到 35 元那笔")
        assertEquals("cat_transport", byAmount[Money.ofCents(2800)]?.categoryId, "28 元的分类应挂到 28 元那笔")
        assertEquals("午饭", byAmount[Money.ofCents(3500)]?.title)
    }

    @Test
    fun countMismatchSkipsMergeAndKeepsRuleResult() = runTest {
        // 云端笔数与规则层对不上时不做合并，避免把云端条目错配到不同笔上。
        val cloud = FakeCloud(AppResult.success(listOf(cloudEntry(), cloudEntry())))
        val result = pipeline(FixedClassifier(emptyList()), cloud).parse("昨天午饭 35 块", REF)
        assertEquals(1, result.entries.size)
        assertNull(result.entries.single().categoryId)
    }

    @Test
    fun cloudFallbackUsedWhenRulesProduceNothing() = runTest {
        val cloud = FakeCloud(AppResult.success(listOf(cloudEntry())))
        val result = pipeline(FixedClassifier(emptyList()), cloud).parse("今天", REF)
        assertEquals(1, cloud.callCount)
        assertEquals(REF, cloud.lastContext?.today)
        assertEquals(Money.ofCents(4200), result.entries.single().amount)
    }

    @Test
    fun cloudFailureFallsBackToRules() = runTest {
        val cloud = FakeCloud(AppResult.Failure(AppError.Network("offline")))
        val result = pipeline(FixedClassifier(emptyList()), cloud).parse("今天", REF)
        assertEquals(1, cloud.callCount)
        assertTrue(result is ParseResult.Partial)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun nullCloudNeverThrows() = runTest {
        val result = pipeline(FixedClassifier(emptyList()), cloud = null).parse("今天", REF)
        assertTrue(result is ParseResult.Partial)
        assertEquals(setOf(ParsedField.AMOUNT), result.unresolved)
    }

    @Test
    fun throwingClassifierDegradesInsteadOfEscaping() = runTest {
        // 分类器违约抛异常时，不得逃逸到调用方；退化为规则层结果（分类保持未识别）。
        val result = pipeline(ThrowingClassifier()).parse("昨天午饭 35 块 微信", REF)
        val entry = result.entries.single()
        assertNull(entry.categoryId)
        assertTrue(entry.unresolved.contains(ParsedField.CATEGORY))
    }

    @Test
    fun throwingCloudDegradesInsteadOfEscaping() = runTest {
        // 云端实现违约抛异常时，回退到规则层结果，不抛给调用方。
        val result = pipeline(FixedClassifier(emptyList()), ThrowingCloud()).parse("昨天午饭 35 块", REF)
        val entry = result.entries.single()
        assertNull(entry.categoryId, "云端抛异常时保持规则层结果")
    }

    @Test
    fun rankedAppliesThresholdAndTopN() {
        val suggestions = listOf(
            CategorySuggestion("a", 0.4f),
            CategorySuggestion("b", 0.9f),
            CategorySuggestion("c", 0.7f),
            CategorySuggestion("d", 0.8f),
            CategorySuggestion("e", 0.6f),
        )
        val ranked = suggestions.ranked()
        assertEquals(listOf("b", "d", "c"), ranked.map { it.categoryId }, "应过滤阈值并取 top-3")
        assertTrue(ranked.none { it.confidence < CategoryClassifier.MIN_CONFIDENCE })
        assertEquals(3, CategoryClassifier.TOP_N)
    }

    private fun cloudEntry(
        amountCents: Long = 4200,
        date: LocalDate = REF,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: String? = "cat_food",
        accountId: String? = "acct_wechat",
        title: String = "午饭",
        unresolved: Set<ParsedField> = emptySet(),
    ) = ParsedEntry(
        amount = Money.ofCents(amountCents),
        type = type,
        date = date,
        time = null,
        accountId = accountId,
        toAccountId = null,
        categoryId = categoryId,
        title = title,
        remark = "",
        confidence = Confidence.MEDIUM,
        amountHasUnit = true,
        unresolved = unresolved,
    )
}
