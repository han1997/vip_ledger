package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsCalculatorTest {

    private val zone = TimeZone.of("Asia/Shanghai")
    private val september = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
    private val august = DateRange(LocalDate(2026, 8, 1), LocalDate(2026, 9, 30))

    private fun at(y: Int, m: Int, d: Int, hour: Int = 12): Long =
        LocalDate(y, m, d).startOfDayEpochMillis(zone) + hour * 3_600_000L

    private fun category(id: String, name: String, kind: CategoryKind, parentId: String? = null) = Category(
        id = id,
        ledgerId = "l1",
        parentId = parentId,
        name = name,
        kind = kind,
        createdAt = 0,
        updatedAt = 0,
    )

    private val categories = listOf(
        category("p-food", "餐饮", CategoryKind.EXPENSE),
        category("c-breakfast", "早餐", CategoryKind.EXPENSE, parentId = "p-food"),
        category("c-lunch", "午餐", CategoryKind.EXPENSE, parentId = "p-food"),
        category("p-traffic", "交通", CategoryKind.EXPENSE),
        category("i-salary", "工资", CategoryKind.INCOME),
    )

    private val tags = listOf(
        Tag(id = "tag-trip", ledgerId = "l1", name = "出差", createdAt = 0, updatedAt = 0),
        Tag(id = "tag-reimburse", ledgerId = "l1", name = "可报销", createdAt = 0, updatedAt = 0),
    )

    private fun txn(
        id: String,
        type: TransactionType,
        amountCents: Long,
        categoryId: String? = null,
        tagIds: List<String> = emptyList(),
        occurredAt: Long = at(2026, 9, 10),
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
        excludeFromStats: Boolean = false,
        accountId: String = "cash",
        deletedAt: Long? = null,
    ) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = accountId,
        toAccountId = if (type == TransactionType.TRANSFER || type == TransactionType.DEBT_REPAY) "other" else null,
        categoryId = categoryId,
        occurredAt = occurredAt,
        draftStatus = draftStatus,
        excludeFromStats = excludeFromStats,
        tagIds = tagIds,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = deletedAt,
    )

    private val txns = listOf(
        txn("e1", TransactionType.EXPENSE, 3_000, categoryId = "p-food", tagIds = listOf("tag-trip"), occurredAt = at(2026, 9, 10)),
        txn("e2", TransactionType.EXPENSE, 2_000, categoryId = "c-breakfast", tagIds = listOf("tag-trip", "tag-reimburse"), occurredAt = at(2026, 9, 11)),
        txn("e3", TransactionType.EXPENSE, 1_000, categoryId = null, occurredAt = at(2026, 9, 12)),
        txn("i1", TransactionType.INCOME, 50_000, categoryId = "i-salary", occurredAt = at(2026, 9, 15)),
        txn("tr1", TransactionType.TRANSFER, 10_000, categoryId = null, occurredAt = at(2026, 9, 16)),
        txn("rp1", TransactionType.DEBT_REPAY, 5_000, categoryId = null, occurredAt = at(2026, 9, 17)),
        txn("ad1", TransactionType.ADJUST, 500, categoryId = null, occurredAt = at(2026, 9, 18)),
        txn("dr1", TransactionType.EXPENSE, 9_900, categoryId = "p-food", draftStatus = DraftStatus.PENDING, occurredAt = at(2026, 9, 19)),
        txn("ex1", TransactionType.EXPENSE, 8_800, categoryId = "p-food", excludeFromStats = true, occurredAt = at(2026, 9, 20)),
        txn("old1", TransactionType.EXPENSE, 7_700, categoryId = "p-food", occurredAt = at(2026, 8, 5)),
    )

    @Test
    fun transferRepaymentAdjustAndDraftsAreExcludedFromStats() {
        val counted = StatsCalculator.countedIn(txns, september, zone).map { it.id }
        assertEquals(listOf("e1", "e2", "e3", "i1"), counted)
        assertTrue(counted.none { it in setOf("tr1", "rp1", "ad1", "dr1", "ex1", "old1") })
    }

    @Test
    fun summarySplitsIncomeExpenseAndBalance() {
        val summary = StatsCalculator.summarize(txns, categories, tags, september, zone)
        assertEquals(Money.ofCents(50_000), summary.income)
        assertEquals(Money.ofCents(6_000), summary.expense)
        assertEquals(Money.ofCents(44_000), summary.balance)
        assertEquals(4, summary.count)
    }

    @Test
    fun categoryStatsRollUpToTopLevelAndKeepChildDetail() {
        val byCategory = StatsCalculator.summarize(txns, categories, tags, september, zone).byCategory
        val expense = byCategory.getValue(CategoryKind.EXPENSE)
        assertEquals(listOf("p-food", null), expense.map { it.categoryId })
        val food = expense.first()
        assertEquals(Money.ofCents(5_000), food.total)
        assertEquals(2, food.count)
        assertEquals(listOf("c-breakfast"), food.children.map { it.categoryId })
        assertEquals(Money.ofCents(2_000), food.children.first().total)
        assertEquals("餐饮", food.name)
        val uncategorized = expense.last()
        assertEquals(Money.ofCents(1_000), uncategorized.total)
        assertEquals(null, uncategorized.name)
        assertEquals(listOf("i-salary"), byCategory.getValue(CategoryKind.INCOME).map { it.categoryId })
    }

    @Test
    fun tagStatsCountTheFullAmountForEveryAttachedTag() {
        val byTag = StatsCalculator.summarize(txns, categories, tags, september, zone).byTag
        assertEquals(listOf("tag-trip", "tag-reimburse"), byTag.map { it.tagId })
        assertEquals(Money.ofCents(5_000), byTag.first().expense)
        assertEquals(2, byTag.first().count)
        assertEquals(Money.ofCents(2_000), byTag.last().expense)
        assertEquals("可报销", byTag.last().name)
    }

    @Test
    fun accountStatsUseTheSourceAccountOnly() {
        val byAccount = StatsCalculator.summarize(txns, categories, tags, september, zone).byAccount
        assertEquals(listOf("cash"), byAccount.map { it.accountId })
        assertEquals(Money.ofCents(6_000), byAccount.first().expense)
        assertEquals(Money.ofCents(50_000), byAccount.first().income)
        assertEquals(Money.ofCents(44_000), byAccount.first().net)
    }

    @Test
    fun periodBucketsCoverTheWholeRangeIncludingEmptyOnes() {
        val monthly = StatsCalculator.byPeriod(txns, com.han1997.vipledger.core.domain.model.PeriodGranularity.MONTH, august, zone)
        assertEquals(2, monthly.size)
        assertEquals(LocalDate(2026, 8, 1), monthly.first().start)
        assertEquals(Money.ofCents(7_700), monthly.first().expense)
        assertEquals(LocalDate(2026, 9, 1), monthly.last().start)
        assertEquals(Money.ofCents(44_000), monthly.last().balance)

        val daily = StatsCalculator.byPeriod(txns, com.han1997.vipledger.core.domain.model.PeriodGranularity.DAY, DateRange(LocalDate(2026, 9, 10), LocalDate(2026, 9, 12)), zone)
        assertEquals(3, daily.size)
        assertEquals(listOf(1, 1, 1), daily.map { it.count })

        val weekly = StatsCalculator.byPeriod(txns, com.han1997.vipledger.core.domain.model.PeriodGranularity.WEEK, DateRange(LocalDate(2026, 9, 7), LocalDate(2026, 9, 20)), zone)
        assertEquals(2, weekly.size)
        assertEquals(LocalDate(2026, 9, 7), weekly.first().start)
        assertEquals(LocalDate(2026, 9, 20), weekly.last().endInclusive)
        assertEquals(1, weekly.last().count)
        assertEquals(3, weekly.first().count)
    }
}