package com.han1997.vipledger.feature.stats.ui.stats

import com.han1997.vipledger.core.domain.model.AccountStat
import com.han1997.vipledger.core.domain.model.CategoryStat
import com.han1997.vipledger.core.domain.model.PeriodGranularity
import com.han1997.vipledger.core.domain.model.StatsReport
import com.han1997.vipledger.core.domain.model.StatsSummary
import com.han1997.vipledger.core.domain.model.TagStat
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class StatsRowsTest {

    private val range = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))

    private val report = StatsReport(
        ledgerId = "l1",
        range = range,
        granularity = PeriodGranularity.DAY,
        summary = StatsSummary(
            range = range,
            income = Money.ofCents(800000),
            expense = Money.ofCents(100000),
            count = 4,
            byCategory = mapOf(
                CategoryKind.EXPENSE to listOf(
                    CategoryStat(
                        categoryId = "food",
                        name = "餐饮",
                        kind = CategoryKind.EXPENSE,
                        total = Money.ofCents(60000),
                        count = 3,
                        children = listOf(
                            CategoryStat(
                                categoryId = "lunch",
                                name = "午餐",
                                kind = CategoryKind.EXPENSE,
                                total = Money.ofCents(60000),
                                count = 3,
                            ),
                        ),
                    ),
                    CategoryStat(
                        categoryId = null,
                        name = null,
                        kind = CategoryKind.EXPENSE,
                        total = Money.ofCents(40000),
                        count = 1,
                    ),
                ),
                CategoryKind.INCOME to listOf(
                    CategoryStat(
                        categoryId = "salary",
                        name = "工资",
                        kind = CategoryKind.INCOME,
                        total = Money.ofCents(800000),
                        count = 1,
                    ),
                ),
            ),
            byTag = listOf(
                TagStat(
                    tagId = "t1",
                    name = "报销",
                    expense = Money.ofCents(30000),
                    income = Money.ofCents(0),
                    count = 2,
                ),
            ),
            byAccount = listOf(
                AccountStat(
                    accountId = "a1",
                    expense = Money.ofCents(100000),
                    income = Money.ofCents(800000),
                    count = 4,
                ),
            ),
        ),
    )

    @Test
    fun categoryRowsFollowTheSelectedKindAndKeepChildrenForExpansion() {
        val expenseRows = statsRowsOf(report, StatsDimension.CATEGORY, CategoryKind.EXPENSE)

        assertEquals(listOf("food", null), expenseRows.map { it.categoryId })
        assertEquals(60000L, expenseRows.first().cents)
        assertEquals("餐饮", expenseRows.first().label)
        assertEquals(listOf("lunch"), expenseRows.first().children.map { it.categoryId })

        val incomeRows = statsRowsOf(report, StatsDimension.CATEGORY, CategoryKind.INCOME)
        assertEquals(listOf("salary"), incomeRows.map { it.categoryId })
    }

    @Test
    fun tagAndAccountRowsCarryTheirOwnDrillIdsAndSelectedAmount() {
        val tagRow = statsRowsOf(report, StatsDimension.TAG, CategoryKind.EXPENSE).single()
        assertEquals("t1", tagRow.tagId)
        assertEquals(30000L, tagRow.cents)

        val incomeTagRow = statsRowsOf(report, StatsDimension.TAG, CategoryKind.INCOME).single()
        assertEquals(0L, incomeTagRow.cents, "标签统计按选择的收入口径取数")

        val accountRow = statsRowsOf(
            report = report,
            dimension = StatsDimension.ACCOUNT,
            kind = CategoryKind.INCOME,
            accountNames = mapOf("a1" to "现金"),
        ).single()
        assertEquals("a1", accountRow.accountId)
        assertEquals("现金", accountRow.label)
        assertEquals(800000L, accountRow.cents)
    }

    @Test
    fun rowAmountsSumUpToTheKindTotal() {
        val rows = statsRowsOf(report, StatsDimension.CATEGORY, CategoryKind.EXPENSE)

        assertEquals(report.summary.expense.cents, rows.sumOf { it.cents }, "分类占比之和必须等于口径合计")
    }

    @Test
    fun timeDimensionHasNoRankedRows() {
        assertTrue(statsRowsOf(report, StatsDimension.TIME, CategoryKind.EXPENSE).isEmpty())
    }
}