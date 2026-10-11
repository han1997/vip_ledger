package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 十万流水规模的域层聚合回归（P8 贯通验收）。
 *
 * 10 万条账目上的 `StatsCalculator.summarize` 与 `TxnDayGrouper.group` 必须口径正确
 * （草稿/已删除/排除统计/转账不参与），且耗时在宽松上限内——只兜住意外退化
 * （如把 O(n) 聚合写成嵌套扫描），不追求精确基准。
 */
class StatsScaleTest {

    private val zone = TimeZone.UTC

    @Test
    fun statsAndDayGroupingStayCorrectAtHundredThousandTxns() {
        val total = 100_000
        val dayMillis = 24 * 60 * 60 * 1000L
        val txns = ArrayList<Txn>(total)
        var expectedExpenseCents = 0L
        var expectedIncomeCents = 0L
        var expectedCount = 0
        val firstDayEpoch = 20_000L
        val days = total / 100 // 1000 个不同日期，每天 100 条

        for (i in 0 until total) {
            val date = LocalDate.fromEpochDays(firstDayEpoch + i / 100)
            val type = when (i % 3) {
                0 -> TransactionType.EXPENSE
                1 -> TransactionType.TRANSFER
                else -> TransactionType.INCOME
            }
            val cents = (i % 500 + 1) * 100L
            val draftStatus = if (i % 97 == 0) DraftStatus.PENDING else DraftStatus.CONFIRMED
            val deleted = i % 101 == 0
            val excluded = i % 103 == 0
            txns += Txn(
                id = "t$i",
                ledgerId = "l",
                type = type,
                amount = Money.ofCents(cents),
                accountId = "a",
                occurredAt = date.toEpochDays() * dayMillis + (i % 1000) * 1000L,
                title = "txn$i",
                draftStatus = draftStatus,
                excludeFromStats = excluded,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = if (deleted) 9L else null,
            )
            val counted = draftStatus == DraftStatus.CONFIRMED && !deleted && !excluded
            if (!counted) continue
            when (type) {
                TransactionType.EXPENSE -> { expectedExpenseCents += cents; expectedCount++ }
                TransactionType.INCOME -> { expectedIncomeCents += cents; expectedCount++ }
                else -> Unit // 转账不参与收支统计
            }
        }

        val range = DateRange(
            LocalDate.fromEpochDays(firstDayEpoch),
            LocalDate.fromEpochDays(firstDayEpoch + days - 1),
        )

        var started = System.nanoTime()
        val summary = StatsCalculator.summarize(txns, emptyList(), emptyList(), range, zone)
        val summarizeMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(expectedExpenseCents, summary.expense.cents, "10 万条支出口径漂移")
        assertEquals(expectedIncomeCents, summary.income.cents)
        assertEquals(expectedCount, summary.count)
        assertTrue(summarizeMs < 10_000, "summarize 耗时 ${summarizeMs}ms，出现退化")

        started = System.nanoTime()
        val groups = TxnDayGrouper.group(txns, zone)
        val groupMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(days, groups.size, "按日分组的天数")
        assertEquals(expectedIncomeCents, groups.sumOf { it.income.cents })
        assertEquals(expectedExpenseCents, groups.sumOf { it.expense.cents })
        assertTrue(groupMs < 10_000, "group 耗时 ${groupMs}ms，出现退化")
    }
}