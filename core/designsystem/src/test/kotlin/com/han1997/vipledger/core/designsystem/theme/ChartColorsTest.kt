package com.han1997.vipledger.core.designsystem.theme

import com.han1997.vipledger.core.model.CategoryKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ChartColorsTest {

    @Test
    fun expenseShareStartsFromExpenseRed() {
        assertEquals(ExpenseRed, chartColorOf(0, CategoryKind.EXPENSE))
        assertEquals(IncomeGreen, chartColorOf(1, CategoryKind.EXPENSE))
    }

    @Test
    fun incomeShareStartsFromIncomeGreen() {
        assertEquals(IncomeGreen, chartColorOf(0, CategoryKind.INCOME))
        assertEquals(TransferBlue, chartColorOf(1, CategoryKind.INCOME))
    }

    @Test
    fun indexWrapsAroundThePalette() {
        assertEquals(chartColorOf(0, CategoryKind.EXPENSE), chartColorOf(8, CategoryKind.EXPENSE))
        assertEquals(chartColorOf(0, CategoryKind.EXPENSE), chartColorOf(-8, CategoryKind.EXPENSE))
    }
}