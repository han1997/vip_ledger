package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.BudgetCalculator
import com.han1997.vipledger.core.domain.fake.*
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.repository.BudgetRepository
import com.han1997.vipledger.core.domain.usecase.budget.SaveBudgetUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveCalendarUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveHomeOverviewUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveLocalDayUseCase
import com.han1997.vipledger.core.domain.usecase.stats.ObserveStatsReportUseCase
import com.han1997.vipledger.core.model.*
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BudgetUseCasesTest {
    private val month = LocalDate(2026, 10, 1)
    private val zone = TimeZone.of("Asia/Shanghai")
    private val now = LocalDate(2026, 10, 9).startOfDayEpochMillis(zone)
    private val ledger = Ledger("l1", "Test", createdAt = 0, updatedAt = 0)
    private val categories = listOf(category("parent"), category("child", "parent"), category("other"))
    private fun category(id: String, parent: String? = null) = Category(id, "l1", parent, id, CategoryKind.EXPENSE, createdAt = 0, updatedAt = 0)
    private fun limit(scope: String = "", amount: Long? = 10000, date: LocalDate = month, ledgerId: String = "l1") = BudgetLimit("$ledgerId/$date/$scope", ledgerId, date, scope, amount?.let(Money::ofCents), 0)
    private fun txn(id: String, cents: Long, type: TransactionType = TransactionType.EXPENSE, at: Long = now) = Txn(id, "l1", type, Money.ofCents(cents), "cash", categoryId = "child", occurredAt = at, createdAt = 0, updatedAt = 0)

    @Test fun parentAndTotalCountChildOnceAndSharePublishedExpenseRules() {
        val entries = listOf(txn("cost", 10000), txn("refund", 2000, TransactionType.REFUND), txn("income", 999, TransactionType.INCOME), txn("transfer", 999, TransactionType.TRANSFER), txn("draft", 999).copy(draftStatus = DraftStatus.PENDING), txn("excluded", 999).copy(excludeFromStats = true), txn("deleted", 999).copy(deletedAt = now), txn("other-ledger", 999).copy(ledgerId = "l2"))
        val report = BudgetCalculator.report("l1", month, listOf(limit(), limit("parent"), limit("child"), limit("other")), entries, categories, zone)
        assertEquals(listOf(8000L, 8000L, 0L, 8000L), report.progress.map { it.spent.cents })
        assertEquals(2000L, report.total!!.remaining.cents)
        assertTrue(report.total!!.reached(80)); assertFalse(report.total!!.reached(100))
    }

    @Test fun refundsReduceReceivedMonthAndCanProduceNegativeExpense() {
        val report = BudgetCalculator.report("l1", month, listOf(limit()), listOf(txn("old", 10000, at = LocalDate(2026, 9, 30).startOfDayEpochMillis(zone)), txn("returned", 6000, TransactionType.REFUND)), categories, zone)
        assertEquals(-6000L, report.total!!.spent.cents)
        assertEquals(16000L, report.total!!.remaining.cents)
        assertEquals(0f, report.total!!.fraction); assertFalse(report.total!!.reached(80))
    }

    @Test fun limitsCarryAcrossYearButNoBalanceCarriesAndNullDisables() {
        val rows = listOf(limit(date = LocalDate(2025, 12, 1)), limit("child", 5000, LocalDate(2026, 1, 1)), limit("child", null, LocalDate(2026, 2, 1)), limit(amount = 777, ledgerId = "l2"))
        val report = BudgetCalculator.report("l1", month, rows, emptyList(), categories, zone)
        assertEquals(1, report.progress.size); assertEquals(10000L, report.total!!.remaining.cents)
        assertNull(BudgetCalculator.effective(rows.filter { it.ledgerId == "l1" }, month).find { it.scopeKey == "child" }!!.amount)
    }

    @Test fun zeroLimitAndLargeThresholdDoNotDivideByZeroOrOverflow() {
        val zero = BudgetProgress(limit(amount = 0), Money.ZERO)
        assertFalse(zero.reached(80)); assertEquals(0f, zero.fraction)
        assertTrue(zero.copy(spent = Money.ofCents(1)).reached(100))
        val large = BudgetProgress(limit(amount = Long.MAX_VALUE), Money.ofCents(Long.MAX_VALUE - 1))
        assertTrue(large.reached(80)); assertFalse(large.reached(100))
        assertFalse(BudgetProgress(limit(amount = 101), Money.ofCents(80)).reached(80))
        assertTrue(BudgetProgress(limit(amount = 101), Money.ofCents(81)).reached(80))
    }

    @Test fun historicalEditPreservesFollowingMonthsIncludingDisabledBudget() = runTest {
        val repo = FakeBudgets(listOf(limit(date = LocalDate(2026, 8, 1))))
        val save = saver(repo)
        assertTrue(save("l1", LocalDate(2026, 9, 1), "", Money.ofCents(5000)).isSuccess)
        assertEquals(5000L, BudgetCalculator.effective(repo.rows.value, LocalDate(2026, 9, 1)).single().amount!!.cents)
        assertEquals(10000L, BudgetCalculator.effective(repo.rows.value, month).single().amount!!.cents)
        assertTrue(save("l1", LocalDate(2026, 7, 1), "child", Money.ofCents(50)).isSuccess)
        assertNull(BudgetCalculator.effective(repo.rows.value, month).find { it.scopeKey == "child" }!!.amount)
        assertTrue(save("l1", month, "", null).isSuccess)
        assertNull(BudgetCalculator.effective(repo.rows.value, LocalDate(2027, 1, 1)).find { it.scopeKey == "" }!!.amount)
    }

    @Test fun savingRejectsCrossLedgerIncomeCategoryNegativeAndMalformedMonth() = runTest {
        val repo = FakeBudgets()
        val catRepo = FakeCategoryRepository((categories + category("foreign").copy(ledgerId = "l2") + category("income").copy(kind = CategoryKind.INCOME)).toMutableList())
        val save = SaveBudgetUseCase(repo, FakeLedgerRepository(mutableListOf(ledger)), catRepo, FakeTransactionRunner(), FakeTimeProvider(now), SequentialIdGenerator())
        assertFalse(save("l1", month, "foreign", Money.ofCents(1)).isSuccess)
        assertFalse(save("l1", month, "income", Money.ofCents(1)).isSuccess)
        assertFalse(save("l1", month, "", Money.ofCents(-1)).isSuccess)
        assertFalse(save("l1", LocalDate(2026, 10, 2), "", Money.ZERO).isSuccess)
        assertTrue(repo.rows.value.isEmpty())
    }

    @Test fun calendarHomeBudgetAndReportAgreeAcrossMonthBoundary() = runTest {
        val time = FakeTimeProvider(LocalDate(2026, 10, 1).startOfDayEpochMillis(zone))
        val entries = listOf(txn("today", 10000, at = time.now), txn("yesterday", 700, at = time.now - 1000), txn("refund", 2000, TransactionType.REFUND, time.now), txn("draft", 555, at = time.now).copy(draftStatus = DraftStatus.PENDING))
        val txns = FakeTxnRepository(entries.toMutableList())
        val home = ObserveHomeOverviewUseCase(FakeLedgerRepository(mutableListOf(ledger)), FakeAccountRepository(), txns, time)("l1").first()
        val calendar = ObserveCalendarUseCase(txns)("l1", month, zone).first()
        val budget = BudgetCalculator.report("l1", month, listOf(limit()), entries, categories, zone)
        val report = ObserveStatsReportUseCase(txns, FakeCategoryRepository(categories.toMutableList()), FakeTagRepository(), time)("l1", DateRangePreset.THIS_MONTH.resolve(month)).first()
        assertEquals(Money.ofCents(8000), home.monthExpense)
        assertEquals(home.monthExpense, Money.sum(calendar.days.map { it.expense }))
        assertEquals(home.monthExpense, budget.total!!.spent)
        assertEquals(home.monthExpense, report.summary.expense)
        assertEquals(8700L, Money.sum(home.lastSevenDays.map { it.expense }).cents)
        assertEquals(7, home.lastSevenDays.size); assertEquals(31, calendar.days.size)
        assertEquals(2, calendar.groups.single().txns.size)
    }

    @Test fun longLivedDayFlowDetectsClockAndZoneChangesWithoutLedgerWrites() = runTest {
        var currentZone = zone
        var millis = LocalDate(2026, 10, 31).startOfDayEpochMillis(zone)
        val time = object : TimeProvider { override fun nowMillis() = millis; override val zone get() = currentZone }
        val dates = mutableListOf<com.han1997.vipledger.core.domain.usecase.home.LocalDay>()
        val job = backgroundScope.launch { ObserveLocalDayUseCase(time)().collect { dates += it } }
        runCurrent()
        millis = LocalDate(2026, 11, 1).startOfDayEpochMillis(zone)
        advanceTimeBy(30_000); runCurrent()
        currentZone = TimeZone.UTC
        advanceTimeBy(30_000); runCurrent()
        assertEquals(listOf(LocalDate(2026, 10, 31), LocalDate(2026, 11, 1), LocalDate(2026, 10, 31)), dates.map { it.date })
        assertEquals(TimeZone.UTC, dates.last().zone)
        job.cancel()
    }

    private fun saver(repo: FakeBudgets) = SaveBudgetUseCase(repo, FakeLedgerRepository(mutableListOf(ledger)), FakeCategoryRepository(categories.toMutableList()), FakeTransactionRunner(), FakeTimeProvider(now), SequentialIdGenerator())
    private class FakeBudgets(initial: List<BudgetLimit> = emptyList()) : BudgetRepository {
        val rows = MutableStateFlow(initial)
        override fun observe(ledgerId: String) = rows.map { it.filter { row -> row.ledgerId == ledgerId } }
        override suspend fun list(ledgerId: String) = rows.value.filter { it.ledgerId == ledgerId }
        override suspend fun save(limits: List<BudgetLimit>): AppResult<Unit> { rows.value = rows.value.filter { row -> limits.none { it.id == row.id } } + limits; return AppResult.success(Unit) }
        override suspend fun claimAlert(ledgerId: String, month: LocalDate, scopeKey: String, threshold: Int, now: Long) = AppResult.success(true)
    }
}
