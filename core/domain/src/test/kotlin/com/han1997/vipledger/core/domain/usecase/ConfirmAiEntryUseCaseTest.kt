package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.fake.FakeMlSampleRepository
import com.han1997.vipledger.core.domain.fake.FakeMerchantRuleRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.nlp.Confidence
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.usecase.txn.ConfirmAiEntryUseCase
import com.han1997.vipledger.core.domain.usecase.txn.LEARNED_RULE_PRIORITY
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.MlSampleSource
import com.han1997.vipledger.core.model.RuleSource
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

    private val useCase = ConfirmAiEntryUseCase(
        txnRepository = txns,
        merchantRuleRepository = merchantRules,
        mlSampleRepository = samples,
        transactionRunner = transactionRunner,
        balanceSyncer = balanceSyncer,
        timeProvider = timeProvider,
        idGenerator = SequentialIdGenerator("id"),
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
}
