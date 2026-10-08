package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeLedgerRepository
import com.han1997.vipledger.core.domain.fake.FakeTagRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.home.ObserveHomeOverviewUseCase
import com.han1997.vipledger.core.domain.usecase.stats.ObserveStatsReportUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * 「统计页的本月数字」必须与「首页本月收支」完全一致。
 *
 * 这是 PRD 风险表里点名的一条：两处如果各算各的，用户会看到两个不同的本月支出。
 * 首页走 [ObserveHomeOverviewUseCase]，统计页走 [ObserveStatsReportUseCase]，
 * 两者共享 `StatsCalculator` 口径，这个测试把这条约定钉死。
 */
class StatsMatchesHomeOverviewTest {

    private val timeProvider = FakeTimeProvider()
    private val zone = timeProvider.zone
    private val today = timeProvider.nowMillis().toLocalDate(zone)
    private val thisMonth = DateRangePreset.THIS_MONTH.resolve(today)

    private val ledger = Ledger(id = LEDGER, name = "默认账本", isDefault = true, createdAt = 0, updatedAt = 0)

    private val accounts = FakeAccountRepository(
        mutableListOf(
            Account(
                id = "a1",
                ledgerId = LEDGER,
                name = "现金",
                type = AccountType.CASH,
                currentBalance = Money.ofCents(100000),
                createdAt = 0,
                updatedAt = 0,
            ),
        ),
    )

    private val categories = FakeCategoryRepository(mutableListOf())
    private val tags = FakeTagRepository(mutableListOf())

    /** 本月收支与退款、草稿、转账和上月消费，两边必须采用同一口径。 */
    private val txns = FakeTxnRepository(
        mutableListOf(
            txn("t1", TransactionType.EXPENSE, 3800, dayOfMonth(5), categoryId = null),
            txn("t2", TransactionType.INCOME, 800000, dayOfMonth(20)),
            txn("t3", TransactionType.EXPENSE, 12000, dayOfMonth(24)),
            txn("t4", TransactionType.EXPENSE, 9999, dayOfMonth(24), draftStatus = DraftStatus.PENDING),
            txn("t5", TransactionType.TRANSFER, 20000, dayOfMonth(24), toAccountId = "a2"),
            txn("t6", TransactionType.EXPENSE, 77777, lastMonthDay()),
            txn("refund", TransactionType.REFUND, 1800, today.startOfDayEpochMillis(zone)).copy(refundOfTxnId = "t6"),
        ),
    )

    @Test
    fun thisMonthStatsReportMatchesHomeOverview() = runTest {
        val report = ObserveStatsReportUseCase(txns, categories, tags, timeProvider)(LEDGER, thisMonth).first()
        val overview = ObserveHomeOverviewUseCase(
            ledgerRepository = FakeLedgerRepository(mutableListOf(ledger)),
            accountRepository = accounts,
            txnRepository = txns,
            timeProvider = timeProvider,
        )(LEDGER).first()

        assertEquals(overview.monthIncome, report.summary.income, "统计页与首页的本月收入必须一致")
        assertEquals(overview.monthExpense, report.summary.expense, "统计页与首页的本月支出必须一致")
        assertEquals(overview.monthCount, report.summary.count, "统计页与首页的本月笔数必须一致")
        assertEquals(Money.ofCents(14000), report.summary.expense)
        assertEquals(Money.ofCents(-1800), overview.todayExpense)
    }

    private fun txn(
        id: String,
        type: TransactionType,
        amountCents: Long,
        occurredAt: Long,
        categoryId: String? = null,
        toAccountId: String? = null,
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
    ) = Txn(
        id = id,
        ledgerId = LEDGER,
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = "a1",
        toAccountId = toAccountId,
        categoryId = categoryId,
        occurredAt = occurredAt,
        draftStatus = draftStatus,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun dayOfMonth(day: Int): Long =
        LocalDate(today.year, today.monthNumber, day).startOfDayEpochMillis(zone) + 9 * 3600_000L

    /** 设备时区下的「上个月最后一天」，确保落在本月区间之外。 */
    private fun lastMonthDay(): Long {
        val firstOfMonth = LocalDate(today.year, today.monthNumber, 1)
        return firstOfMonth.startOfDayEpochMillis(zone) - 24 * 3600_000L
    }

    private companion object {
        const val LEDGER = "l1"
    }
}
