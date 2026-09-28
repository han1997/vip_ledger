package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.fake.FakeMlSampleRepository
import com.han1997.vipledger.core.domain.fake.FakeMerchantRuleRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.ml.MlModelTrainer
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.nlp.Confidence
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.usecase.txn.ConfirmAiEntryUseCase
import com.han1997.vipledger.core.domain.usecase.txn.LEARNED_RULE_PRIORITY
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.MlSampleSource
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * AI 记账「确认入账 + 修正沉淀」的领域规则（PRD Requirement 4 / 7、D5#2、AC）。
 *
 * 覆盖：修正写训练样本 + `source = LEARNED` 商户规则、同商户不产生重复规则、
 * LEARNED 优先级低于用户手写规则（数值更大）、无修正时不写入、跨表写入在同一事务内。
 */
class ConfirmAiEntryUseCaseTest {

    private val ledgerId = "l1"
    private val txns = FakeTxnRepository()
    private val merchantRules = FakeMerchantRuleRepository()
    private val samples = FakeMlSampleRepository()
    private val transactionRunner = FakeTransactionRunner()
    private val balanceSyncer = RecordingBalanceSyncer()
    private val timeProvider = FakeTimeProvider()

    /** 记录被通知训练的账本 id，用于断言「修正后才触发、无修正不触发」（PRD Requirement 3）。 */
    private val trainedLedgers = mutableListOf<String>()

    private val useCase = ConfirmAiEntryUseCase(
        txnRepository = txns,
        merchantRuleRepository = merchantRules,
        mlSampleRepository = samples,
        transactionRunner = transactionRunner,
        balanceSyncer = balanceSyncer,
        timeProvider = timeProvider,
        idGenerator = SequentialIdGenerator("id"),
        modelTrainer = MlModelTrainer { ledgerId -> trainedLedgers += ledgerId },
    )

    private suspend fun seed(txnId: String, title: String, categoryId: String): Txn = Txn(
        id = txnId,
        ledgerId = ledgerId,
        type = TransactionType.EXPENSE,
        amount = Money.ofCents(3500),
        accountId = "a1",
        categoryId = categoryId,
        occurredAt = 0,
        title = title,
        remark = "",
        source = TxnSource.AI,
        draftStatus = DraftStatus.PENDING,
        createdAt = 0,
        updatedAt = 0,
    ).also { txns.insert(it) }

    private fun parsed(categoryId: String?, title: String): ParsedEntry = ParsedEntry(
        amount = Money.ofCents(3500),
        type = TransactionType.EXPENSE,
        date = LocalDate(2026, 9, 27),
        time = null,
        accountId = "a1",
        toAccountId = null,
        categoryId = categoryId,
        title = title,
        remark = "",
        confidence = Confidence.MEDIUM,
        amountHasUnit = true,
        unresolved = emptySet(),
    )

    @Test
    fun correctionWritesSampleAndLearnedRule() = runTest {
        val txn = seed("t1", title = "午饭", categoryId = "c_old")
        // 用户把分类从 c_old 改成 c_food → 判定为修正
        val result = useCase(txn.copy(categoryId = "c_food"), parsed("c_old", "午饭"))

        assertTrue(result.isSuccess)
        assertEquals(DraftStatus.CONFIRMED, txns.getById("t1")?.draftStatus)

        val storedSamples = samples.listByLedger(ledgerId)
        assertEquals(1, storedSamples.size)
        assertEquals(MlSampleSource.USER_CORRECTION, storedSamples.single().source)
        assertEquals("c_food", storedSamples.single().categoryId)
        assertEquals("午饭", storedSamples.single().text)

        val rules = merchantRules.listByLedger(ledgerId)
        assertEquals(1, rules.size)
        assertEquals(RuleSource.LEARNED, rules.single().source)
        assertEquals("午饭", rules.single().pattern)
        assertEquals("c_food", rules.single().categoryId)
        assertTrue(rules.single().priority > 100, "LEARNED 优先级数值必须大于 USER 默认 100")
        assertEquals(LEARNED_RULE_PRIORITY, rules.single().priority)
    }

    @Test
    fun repeatedCorrectionDoesNotDuplicateRule() = runTest {
        seed("t1", title = "午饭", categoryId = "c_old")
        seed("t2", title = "午饭", categoryId = "c_old")

        useCase(txns.getById("t1")!!.copy(categoryId = "c_food"), parsed("c_old", "午饭"))
        useCase(txns.getById("t2")!!.copy(categoryId = "c_food"), parsed("c_old", "午饭"))

        val rules = merchantRules.listByLedger(ledgerId)
        assertEquals(1, rules.size, "同一商户重复修正不得产生重复规则")
        assertEquals("午饭", rules.single().pattern)
        // 训练样本仍应每次修正各写一条
        assertEquals(2, samples.listByLedger(ledgerId).size)
    }

    @Test
    fun promotedRuleIsNotDuplicatedByLaterLearning() = runTest {
        // 学习规则在管理页被「提升」为 USER 后，同商户再修正不得再写一条重复的 LEARNED 规则，
        // 否则「重复修正不得产生重复规则」被破坏、管理页出现两条同 pattern 规则（PR5 交互）。
        merchantRules.upsert(
            MerchantRule(
                id = "r1",
                ledgerId = ledgerId,
                pattern = "午饭",
                matchType = RuleMatchType.CONTAINS,
                targetField = RuleTargetField.ANY,
                categoryId = "c_food",
                priority = LEARNED_RULE_PRIORITY,
                source = RuleSource.USER,
                enabled = true,
                hitCount = 3,
                createdAt = 111L,
                updatedAt = 111L,
            ),
        )
        seed("t1", title = "午饭", categoryId = "c_old")

        useCase(txns.getById("t1")!!.copy(categoryId = "c_food"), parsed("c_old", "午饭"))

        val rules = merchantRules.listByLedger(ledgerId)
        assertEquals(1, rules.size, "提升后的规则不得被学习路径再写一条重复规则")
        assertEquals(RuleSource.USER, rules.single().source, "学习路径不得把已提升规则改回 LEARNED")
        assertEquals(3, rules.single().hitCount, "学习路径不得改动已提升规则的命中次数")
        // 修正样本仍应写入（Requirement 4 与「学习规则」解耦）
        assertEquals(1, samples.listByLedger(ledgerId).size)
    }

    @Test
    fun learningUpdatesExistingLearnedRuleInPlace() = runTest {
        merchantRules.upsert(
            MerchantRule(
                id = "r1",
                ledgerId = ledgerId,
                pattern = "午饭",
                matchType = RuleMatchType.CONTAINS,
                targetField = RuleTargetField.ANY,
                categoryId = "c_old",
                priority = LEARNED_RULE_PRIORITY,
                source = RuleSource.LEARNED,
                enabled = true,
                hitCount = 4,
                createdAt = 111L,
                updatedAt = 111L,
            ),
        )
        seed("t1", title = "午饭", categoryId = "c_old")

        useCase(txns.getById("t1")!!.copy(categoryId = "c_food"), parsed("c_old", "午饭"))

        val rules = merchantRules.listByLedger(ledgerId)
        assertEquals(1, rules.size, "已存在的 LEARNED 规则应原地更新，不新增")
        assertEquals("r1", rules.single().id)
        assertEquals("c_food", rules.single().categoryId)
        assertEquals(4, rules.single().hitCount, "原地更新不得重置命中次数")
        assertEquals(111L, rules.single().createdAt)
    }

    @Test
    fun noCorrectionWritesNothingButStillConfirms() = runTest {
        val txn = seed("t1", title = "午饭", categoryId = "c_food")
        val result = useCase(txn, parsed("c_food", "午饭"))

        assertTrue(result.isSuccess)
        assertEquals(DraftStatus.CONFIRMED, txns.getById("t1")?.draftStatus)
        assertEquals(0, samples.listByLedger(ledgerId).size)
        assertEquals(0, merchantRules.listByLedger(ledgerId).size)
    }

    @Test
    fun confirmSyncsBalancesInSingleTransaction() = runTest {
        val txn = seed("t1", title = "午饭", categoryId = "c_food")
        useCase(txn, parsed("c_food", "午饭"))

        assertEquals(1, transactionRunner.transactionCount)
        assertEquals(listOf("a1"), balanceSyncer.syncedIds)
    }

    @Test
    fun rejectsDraftThatIsNotPending() = runTest {
        val txn = seed("t1", title = "午饭", categoryId = "c_food")
            .copy(draftStatus = DraftStatus.CONFIRMED)
        txns.update(txn)

        val result = useCase(txn, parsed("c_food", "午饭"))
        assertFalse(result.isSuccess)
        assertNotNull(txns.getById("t1"))
    }

    @Test
    fun correctionNotifiesModelTrainerAfterCommit() = runTest {
        val txn = seed("t1", title = "午饭", categoryId = "c_old")
        useCase(txn.copy(categoryId = "c_food"), parsed("c_old", "午饭"))

        assertEquals(listOf(ledgerId), trainedLedgers, "修正写入样本后应通知自动训练触发点")
    }

    @Test
    fun noCorrectionDoesNotNotifyModelTrainer() = runTest {
        val txn = seed("t1", title = "午饭", categoryId = "c_food")
        useCase(txn, parsed("c_food", "午饭"))

        assertTrue(trainedLedgers.isEmpty(), "没有修正就没有样本，不应触发训练")
    }

    @Test
    fun rejectedConfirmDoesNotNotifyModelTrainer() = runTest {
        val txn = seed("t1", title = "午饭", categoryId = "c_food")
            .copy(draftStatus = DraftStatus.CONFIRMED)
        txns.update(txn)

        useCase(txn, parsed("c_old", "午饭"))
        assertTrue(trainedLedgers.isEmpty(), "确认失败不得触发训练")
    }
}
