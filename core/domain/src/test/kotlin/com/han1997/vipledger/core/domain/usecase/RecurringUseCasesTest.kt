package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeRecurringRuleRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.usecase.recurring.GenerateRecurringTxnsUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.RecurringGenerationReport
import com.han1997.vipledger.core.domain.usecase.recurring.RecurringRuleInput
import com.han1997.vipledger.core.domain.usecase.recurring.SaveRecurringRuleUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ConfirmDraftUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DiscardDraftUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class RecurringUseCasesTest {

    private val zone = TimeZone.of("Asia/Shanghai")
    private val cash = Account(
        id = "cash",
        ledgerId = "l1",
        name = "现金",
        type = AccountType.CASH,
        createdAt = 0,
        updatedAt = 0,
    )

    /** 2026-01-03 12:00 上海时间。 */
    private fun timeProvider() = FakeTimeProvider(now = LocalDate(2026, 1, 3).toDateColumnEpochMillis() + 12 * 3600_000L)

    private fun rule(
        freq: RecurrenceFrequency = RecurrenceFrequency.DAILY,
        start: LocalDate = LocalDate(2026, 1, 1),
        interval: Int = 1,
        endType: RecurrenceEndType = RecurrenceEndType.NEVER,
        maxOccurrences: Int? = null,
        generatedCount: Int = 0,
        generateMode: GenerateMode = GenerateMode.DRAFT,
        missedPolicy: MissedPolicy = MissedPolicy.SKIP,
        enabled: Boolean = true,
    ) = RecurringRule(
        id = "rule-1",
        ledgerId = "l1",
        name = "房租",
        txnType = TransactionType.EXPENSE,
        amount = Money.ofYuan(2000),
        accountId = "cash",
        freq = freq,
        interval = interval,
        startAt = start.toDateColumnEpochMillis(),
        endType = endType,
        maxOccurrences = maxOccurrences,
        generatedCount = generatedCount,
        generateMode = generateMode,
        missedPolicy = missedPolicy,
        enabled = enabled,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun generator(
        ruleRepository: FakeRecurringRuleRepository,
        txnRepository: FakeTxnRepository = FakeTxnRepository(zone = zone),
        syncer: RecordingBalanceSyncer = RecordingBalanceSyncer(),
        runner: FakeTransactionRunner = FakeTransactionRunner(),
    ) = GenerateRecurringTxnsUseCase(
        recurringRuleRepository = ruleRepository,
        txnRepository = txnRepository,
        accountRepository = FakeAccountRepository(mutableListOf(cash)),
        categoryRepository = FakeCategoryRepository(),
        transactionRunner = runner,
        balanceSyncer = syncer,
        timeProvider = timeProvider(),
        idGenerator = SequentialIdGenerator("txn"),
    )

    @Test
    fun catchUpGeneratesEveryMissedDraft() = runTest {
        val ruleRepository = FakeRecurringRuleRepository(mutableListOf(rule(missedPolicy = MissedPolicy.CATCH_UP)))
        val txns = FakeTxnRepository(zone = zone)
        val syncer = RecordingBalanceSyncer()
        val report = assertIs<AppResult.Success<RecurringGenerationReport>>(
            generator(ruleRepository, txns, syncer)(ledgerId = "l1"),
        ).value

        assertEquals(3, report.generated)
        assertEquals(1, report.rulesProcessed)
        val generated = txns.snapshot()
        assertEquals(listOf(LocalDate(2026, 1, 1), LocalDate(2026, 1, 2), LocalDate(2026, 1, 3)), generated.map { it.occurredAt.toLocalDate(zone) })
        assertTrue(generated.all { it.draftStatus == DraftStatus.PENDING })
        assertTrue(generated.all { it.source == TxnSource.RECURRING && it.recurringRuleId == "rule-1" })
        assertTrue(syncer.calls.isEmpty(), "草稿不参与余额")

        val updated = assertNotNull(ruleRepository.getById("rule-1"))
        assertEquals(3, updated.generatedCount)
        assertEquals(
            LocalDate(2026, 1, 4).toDateColumnEpochMillis(),
            updated.nextOccurrenceAt,
        )
    }

    @Test
    fun skipOnlyGeneratesTheLatestButStillAdvancesTheCursor() = runTest {
        val ruleRepository = FakeRecurringRuleRepository(mutableListOf(rule(missedPolicy = MissedPolicy.SKIP)))
        val txns = FakeTxnRepository(zone = zone)
        val report = assertIs<AppResult.Success<RecurringGenerationReport>>(
            generator(ruleRepository, txns)(ledgerId = "l1"),
        ).value

        assertEquals(1, report.generated)
        assertEquals(listOf(LocalDate(2026, 1, 3)), txns.snapshot().map { it.occurredAt.toLocalDate(zone) })
        assertEquals(3, assertNotNull(ruleRepository.getById("rule-1")).generatedCount)
    }

    @Test
    fun autoModeConfirmsTheTransactionAndSyncsBalances() = runTest {
        val ruleRepository = FakeRecurringRuleRepository(mutableListOf(rule(generateMode = GenerateMode.AUTO)))
        val txns = FakeTxnRepository(zone = zone)
        val syncer = RecordingBalanceSyncer()
        assertIs<AppResult.Success<RecurringGenerationReport>>(
            generator(ruleRepository, txns, syncer)(ledgerId = "l1"),
        )

        assertTrue(txns.snapshot().all { it.draftStatus == DraftStatus.CONFIRMED })
        assertEquals(listOf("cash"), syncer.syncedIds)
    }

    @Test
    fun generationIsIdempotent() = runTest {
        val ruleRepository = FakeRecurringRuleRepository(mutableListOf(rule(missedPolicy = MissedPolicy.CATCH_UP)))
        val txns = FakeTxnRepository(zone = zone)
        val useCase = generator(ruleRepository, txns)
        assertIs<AppResult.Success<RecurringGenerationReport>>(useCase("l1"))
        val second = assertIs<AppResult.Success<RecurringGenerationReport>>(useCase("l1")).value

        assertEquals(0, second.generated)
        assertEquals(3, txns.snapshot().size)
    }

    @Test
    fun countEndStopsGeneratingAndClearsTheCursor() = runTest {
        val ruleRepository = FakeRecurringRuleRepository(
            mutableListOf(rule(endType = RecurrenceEndType.COUNT, maxOccurrences = 2, missedPolicy = MissedPolicy.CATCH_UP)),
        )
        val txns = FakeTxnRepository(zone = zone)
        val useCase = generator(ruleRepository, txns)
        assertEquals(2, assertIs<AppResult.Success<RecurringGenerationReport>>(useCase("l1")).value.generated)

        val updated = assertNotNull(ruleRepository.getById("rule-1"))
        assertEquals(2, updated.generatedCount)
        kotlin.test.assertNull(updated.nextOccurrenceAt)
        assertEquals(0, assertIs<AppResult.Success<RecurringGenerationReport>>(useCase("l1")).value.generated)
    }

    @Test
    fun disabledRulesAreNotTouched() = runTest {
        val ruleRepository = FakeRecurringRuleRepository(mutableListOf(rule(enabled = false)))
        val txns = FakeTxnRepository(zone = zone)
        assertEquals(0, assertIs<AppResult.Success<RecurringGenerationReport>>(generator(ruleRepository, txns)("l1")).value.generated)
        assertTrue(txns.snapshot().isEmpty())
    }

    @Test
    fun saveValidatesAndComputesTheCursor() = runTest {
        val repository = FakeRecurringRuleRepository()
        val useCase = SaveRecurringRuleUseCase(
            recurringRuleRepository = repository,
            accountRepository = FakeAccountRepository(mutableListOf(cash)),
            categoryRepository = FakeCategoryRepository(),
            timeProvider = timeProvider(),
            idGenerator = SequentialIdGenerator("rule"),
        )
        val saved = assertIs<AppResult.Success<RecurringRule>>(
            useCase(
                RecurringRuleInput(
                    ledgerId = "l1",
                    name = "房租",
                    txnType = TransactionType.EXPENSE,
                    amount = Money.ofYuan(2000),
                    accountId = "cash",
                    freq = RecurrenceFrequency.MONTHLY,
                    byMonthDay = 31,
                    startDate = LocalDate(2026, 1, 31),
                ),
            ),
        ).value
        assertEquals(LocalDate(2026, 1, 31).toDateColumnEpochMillis(), saved.nextOccurrenceAt)
        assertEquals(0, saved.generatedCount)

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(input(name = " "))).error)
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(input(txnType = TransactionType.ADJUST))).error)
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(input(accountId = "nope"))).error)
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(input(interval = 0))).error)
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                useCase(input(endType = RecurrenceEndType.COUNT, maxOccurrences = null)),
            ).error,
        )
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                useCase(
                    input(
                        endType = RecurrenceEndType.UNTIL_DATE,
                        endDate = LocalDate(2025, 12, 31),
                    ),
                ),
            ).error,
        )
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                useCase(input(txnType = TransactionType.TRANSFER, toAccountId = "cash")),
            ).error,
        )
    }

    private fun input(
        name: String = "房租",
        txnType: TransactionType = TransactionType.EXPENSE,
        accountId: String = "cash",
        toAccountId: String? = null,
        interval: Int = 1,
        endType: RecurrenceEndType = RecurrenceEndType.NEVER,
        endDate: LocalDate? = null,
        maxOccurrences: Int? = null,
    ) = RecurringRuleInput(
        ledgerId = "l1",
        name = name,
        txnType = txnType,
        amount = Money.ofYuan(2000),
        accountId = accountId,
        toAccountId = toAccountId,
        freq = RecurrenceFrequency.MONTHLY,
        interval = interval,
        startDate = LocalDate(2026, 1, 1),
        endType = endType,
        endDate = endDate,
        maxOccurrences = maxOccurrences,
    )

    @Test
    fun confirmingADraftMakesItCountAndSyncsBalances() = runTest {
        val txns = FakeTxnRepository(zone = zone)
        val pending = txn(draftStatus = DraftStatus.PENDING)
        txns.insert(pending)
        val syncer = RecordingBalanceSyncer()
        val useCase = ConfirmDraftUseCase(txns, FakeTransactionRunner(), syncer, timeProvider())

        val confirmed = assertIs<AppResult.Success<Txn>>(useCase("t1")).value
        assertEquals(DraftStatus.CONFIRMED, confirmed.draftStatus)
        assertEquals(listOf("cash"), syncer.syncedIds)
        assertEquals(0, txns.list(TxnFilter(ledgerId = "l1", draftStatus = DraftStatus.PENDING)).size)
    }

    @Test
    fun discardingADraftHidesItEverywhere() = runTest {
        val txns = FakeTxnRepository(zone = zone)
        txns.insert(txn(draftStatus = DraftStatus.PENDING))
        val useCase = DiscardDraftUseCase(txns, FakeTransactionRunner(), timeProvider())

        assertIs<AppResult.Success<Unit>>(useCase("t1"))
        assertTrue(txns.list(TxnFilter(ledgerId = "l1")).isEmpty())
        assertEquals(DraftStatus.DISCARDED, assertNotNull(txns.getById("t1")).draftStatus)
    }

    private fun txn(draftStatus: DraftStatus) = Txn(
        id = "t1",
        ledgerId = "l1",
        type = TransactionType.EXPENSE,
        amount = Money.ofYuan(10),
        accountId = "cash",
        occurredAt = 0,
        title = "草稿",
        draftStatus = draftStatus,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun observePendingDraftsOnlyReturnsPendingOnes() = runTest {
        val txns = FakeTxnRepository(zone = zone)
        txns.insert(txn(draftStatus = DraftStatus.PENDING))
        txns.insert(txn(draftStatus = DraftStatus.CONFIRMED).copy(id = "t2"))
        val useCase = com.han1997.vipledger.core.domain.usecase.recurring.ObservePendingDraftsUseCase(txns)

        assertEquals(listOf("t1"), useCase("l1").first().map { it.id })
    }
}
