package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TxnFilterSqlTest {

    private val zone = TimeZone.of("Asia/Shanghai")

    @Test
    fun csvWrapsCommasAndTreatsEmptyAsNoFilter() {
        assertNull(TxnFilterSql.csv(emptyList()))
        assertEquals(",a,b,", TxnFilterSql.csv(listOf("a", "b")))
    }

    @Test
    fun emptyFilterBecomesNoConditionsAtAll() {
        val args = TxnFilterSql.of(TxnFilter(ledgerId = "l1", types = emptySet()), zone)
        assertNull(args.from)
        assertNull(args.to)
        assertNull(args.keyword)
        assertNull(args.typeCsv)
        assertNull(args.categoryCsv)
        assertNull(args.accountCsv)
        assertNull(args.tagCsv)
        assertNull(args.minAmountCents)
        assertNull(args.maxAmountCents)
        assertNull(args.draftStatus)
        assertEquals(TxnFilterSql.NO_LIMIT, args.limit)
    }

    @Test
    fun everyDimensionIsEncodedForTheSqlWhereClause() {
        val filter = TxnFilter(
            ledgerId = "l1",
            keyword = "午饭",
            range = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
            categoryIds = setOf("food", "breakfast"),
            tagIds = setOf("trip"),
            accountIds = setOf("cash"),
            types = setOf(TransactionType.EXPENSE),
            minAmount = Money.ofCents(100),
            maxAmount = Money.ofCents(100_000),
            limit = 20,
        )
        val args = TxnFilterSql.of(filter, zone)
        assertEquals(LocalDate(2026, 9, 1).startOfDayEpochMillis(zone), args.from)
        assertEquals(LocalDate(2026, 10, 1).startOfDayEpochMillis(zone), args.to)
        assertEquals("午饭", args.keyword)
        assertEquals(",EXPENSE,", args.typeCsv)
        assertEquals(",food,breakfast,", args.categoryCsv)
        assertEquals(",cash,", args.accountCsv)
        assertEquals(",trip,", args.tagCsv)
        assertEquals(100L, args.minAmountCents)
        assertEquals(100_000L, args.maxAmountCents)
        assertEquals(20, args.limit)
    }

    @Test
    fun blankKeywordIsIgnored() {
        assertNull(TxnFilterSql.of(TxnFilter(ledgerId = "l1", keyword = "   "), zone).keyword)
    }
}