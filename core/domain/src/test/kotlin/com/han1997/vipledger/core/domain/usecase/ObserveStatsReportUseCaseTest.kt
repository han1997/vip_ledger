package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeTagRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.stats.ObserveStatsReportUseCase
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ObserveStatsReportUseCaseTest {

    private val timeProvider = FakeTimeProvider()
    private val september = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))

    private val categories = FakeCategoryRepository(
        mutableListOf(
            category("food", name = "餐饮"),
            category("lunch", name = "午餐", parentId = "food"),
            category("salary", name = "工资", kind = CategoryKind.INCOME),
        ),
    )

    private val tags = FakeTagRepository(mutableListOf(Tag(id = "tag1", ledgerId = LEDGER, name = "报销", createdAt = 0, updatedAt = 0)))

    private val txns = FakeTxnRepository(
        mutableListOf(
            txn("t1", TransactionType.EXPENSE, 3000, day(24), categoryId = "lunch", tagIds = listOf("tag1")),
            txn("t2", TransactionType.INCOME, 800000, day(20), categoryId = "salary"),
            txn("t3", TransactionType.TRANSFER, 20000, day(20), toAccountId = "a2"),
            txn("t4", TransactionType.EXPENSE, 9999, day(24), categoryId = "food", draftStatus = DraftStatus.PENDING),
            txn("t5", TransactionType.EXPENSE, 5000, day(24), categoryId = "food", excludeFromStats = true),
            txn("t6", TransactionType.EXPENSE, 2000, day(24), categoryId = "food", deleted = true),
            txn("t7", TransactionType.EXPENSE, 1000, augustMorning(), categoryId = "food"),
        ),
    )

    private fun useCase() = ObserveStatsReportUseCase(txns, categories, tags, timeProvider)

    @Test
    fun summaryFollowsStatsRules() = runTest {
        val report = useCase()(LEDGER, september).first()

        assertEquals(Money.ofCents(800000), report.summary.income)
        assertEquals(Money.ofCents(3000), report.summary.expense, "转账/草稿/排除统计/已删/区间外都不算")
        assertEquals(2, report.summary.count)
        assertEquals(Money.ofCents(797000), report.summary.balance)
    }

    @Test
    fun categoryStatsRollUpToTopLevelWithChildrenDetail() = runTest {
        val report = useCase()(LEDGER, september).first()
        val expense = report.summary.byCategory.getValue(CategoryKind.EXPENSE)

        assertEquals(listOf("food"), expense.map { it.categoryId })
        assertEquals(Money.ofCents(3000), expense.first().total)
        assertEquals(listOf("lunch"), expense.first().children.map { it.categoryId })
        assertEquals(Money.ofCents(3000), expense.first().children.first().total)
    }

    @Test
    fun tagAndAccountStatsIncludeTheirTxns() = runTest {
        val report = useCase()(LEDGER, september).first()

        assertEquals(listOf("tag1"), report.summary.byTag.map { it.tagId })
        assertEquals(Money.ofCents(3000), report.summary.byTag.first().expense)
        assertEquals(listOf("a1"), report.summary.byAccount.map { it.accountId })
        assertEquals(2, report.summary.byAccount.first().count)
    }

    @Test
    fun dailyBucketsCoverEveryDayAndSumToSummary() = runTest {
        val report = useCase()(LEDGER, september).first()

        assertEquals(30, report.periods.size)
        assertEquals(Money.ofCents(800000), Money.sum(report.periods.map { it.income }))
        assertEquals(Money.ofCents(3000), Money.sum(report.periods.map { it.expense }))
        assertEquals(2, report.periods.sumOf { it.count })
        assertEquals(LocalDate(2026, 9, 24), report.periods.first { it.count > 0 && it.expense.isPositive }.start)
    }

    @Test
    fun monthlyBucketsSplitByMonth() = runTest {
        val range = DateRange(LocalDate(2026, 8, 1), LocalDate(2026, 10, 31))
        val report = useCase()(LEDGER, range, com.han1997.vipledger.core.domain.model.PeriodGranularity.MONTH)
            .first()

        assertEquals(3, report.periods.size)
        assertEquals(Money.ofCents(1000), report.periods[0].expense, "8 月那笔")
        assertEquals(Money.ofCents(3000), report.periods[1].expense, "9 月那笔")
        assertEquals(Money.ofCents(0), report.periods[2].expense)
    }

    @Test
    fun emptyRangeReportsZeros() = runTest {
        val range = DateRange(LocalDate(2025, 1, 1), LocalDate(2025, 1, 31))
        val report = useCase()(LEDGER, range).first()

        assertEquals(Money.ZERO, report.summary.income)
        assertEquals(Money.ZERO, report.summary.expense)
        assertEquals(0, report.summary.count)
        assertEquals(true, report.isEmpty)
    }

    private fun category(id: String, name: String, parentId: String? = null, kind: CategoryKind = CategoryKind.EXPENSE) =
        Category(
            id = id,
            ledgerId = LEDGER,
            parentId = parentId,
            name = name,
            kind = kind,
            createdAt = 0,
            updatedAt = 0,
        )

    private fun txn(
        id: String,
        type: TransactionType,
        amountCents: Long,
        occurredAt: Long,
        categoryId: String? = null,
        toAccountId: String? = null,
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
        excludeFromStats: Boolean = false,
        deleted: Boolean = false,
        tagIds: List<String> = emptyList(),
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
        excludeFromStats = excludeFromStats,
        tagIds = tagIds,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = if (deleted) 1L else null,
    )

    private fun day(day: Int): Long = epochMillis(2026, 9, day)

    private fun augustMorning(): Long = epochMillis(2026, 8, 31)

    private fun epochMillis(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).startOfDayEpochMillis(ZONE) + 9 * 3600_000L

    private companion object {
        const val LEDGER = "l1"
        val ZONE: TimeZone = TimeZone.of("Asia/Shanghai")
    }
}