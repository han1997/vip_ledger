package com.han1997.vipledger.feature.ledger.ui.calendar

import com.han1997.vipledger.feature.ledger.ui.common.validCalendarDate
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.budget.BudgetEditor
import kotlinx.datetime.LocalDate
import kotlin.test.*

class CalendarUiStateTest {
    @Test fun compactAmountsKeepSignAndNonzeroCents() {
        assertEquals("100", calendarAmountText(com.han1997.vipledger.core.model.money.Money.ofCents(10000)))
        assertEquals("10.1", calendarAmountText(com.han1997.vipledger.core.model.money.Money.ofCents(1010)))
        assertEquals("-0.01", calendarAmountText(com.han1997.vipledger.core.model.money.Money.ofCents(-1)))
    }
    @Test fun leapMonthHasExactly29DaysWithMondayFirst() {
        val weeks = calendarWeeks(LocalDate(2024, 2, 1))
        assertEquals(3, weeks.first().takeWhile { it == null }.size)
        assertEquals(29, weeks.flatten().filterNotNull().size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(LocalDate(2024, 2, 29), weeks.flatten().filterNotNull().last())
    }
    @Test fun sundayStartAndSixWeekMonthsKeepOffsets() {
        assertEquals(6, calendarWeeks(LocalDate(2026, 3, 1)).size)
        assertEquals(6, calendarWeeks(LocalDate(2026, 3, 1)).first().count { it == null })
        assertEquals(4, calendarWeeks(LocalDate(2021, 2, 1)).size)
    }
    @Test fun malformedSavedDateFallsBackAndNewDateRouteKeepsTxnIdentity() {
        assertNull(validCalendarDate("2026-02-30")); assertNull(validCalendarDate("")); assertNull(validCalendarDate("1800-01-01"))
        assertEquals(LocalDate(2026, 10, 9), validCalendarDate("2026-10-09"))
        assertEquals("ledger/txn/edit/l1?txnId=&date=2026-10-09", LedgerRoutes.txnEdit("l1", date = LocalDate(2026, 10, 9)))
        assertEquals("ledger/txn/edit/l1?txnId=t1&date=", LedgerRoutes.txnEdit("l1", "t1"))
    }
    @Test fun budgetFormUsesMoneyParserAndAcceptsZero() {
        assertEquals(123456L, BudgetEditor("", "¥1,234.56").amount!!.cents)
        assertEquals(0L, BudgetEditor("", "0").amount!!.cents)
        assertNull(BudgetEditor("", "-10").amount); assertNull(BudgetEditor("", "").amount)
    }
}
