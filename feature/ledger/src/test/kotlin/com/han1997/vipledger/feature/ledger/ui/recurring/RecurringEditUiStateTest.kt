package com.han1997.vipledger.feature.ledger.ui.recurring

import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** 周期规则编辑表单的纯函数：预检、分类过滤、字段显隐。 */
class RecurringEditUiStateTest {

    private fun category(id: String, kind: CategoryKind, parentId: String? = null) = Category(
        id = id,
        ledgerId = LEDGER_ID,
        parentId = parentId,
        name = id,
        kind = kind,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun stateOf(
        name: String = "房租",
        txnType: TransactionType = TransactionType.EXPENSE,
        amount: String = "2000",
        accountId: String? = "cash",
        toAccountId: String? = null,
        freq: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: String = "1",
        endType: RecurrenceEndType = RecurrenceEndType.NEVER,
        endDate: LocalDate? = null,
        maxOccurrences: String = "",
    ) = RecurringEditUiState(
        isLoading = false,
        ledgerId = LEDGER_ID,
        name = name,
        txnType = txnType,
        amountInput = amount,
        accountId = accountId,
        toAccountId = toAccountId,
        freq = freq,
        intervalInput = interval,
        startDate = LocalDate(2026, 1, 1),
        endType = endType,
        endDate = endDate,
        maxOccurrencesInput = maxOccurrences,
    )

    @Test
    fun validStateHasNoError() {
        assertNull(stateOf().validationError())
    }

    @Test
    fun blankNameIsRejected() {
        assertEquals(RecurringEditError.NAME_REQUIRED, stateOf(name = "  ").validationError())
    }

    @Test
    fun nonPositiveAmountIsRejected() {
        assertEquals(RecurringEditError.INVALID_AMOUNT, stateOf(amount = "0").validationError())
        assertEquals(RecurringEditError.INVALID_AMOUNT, stateOf(amount = "").validationError())
        assertEquals(RecurringEditError.INVALID_AMOUNT, stateOf(amount = "abc").validationError())
    }

    @Test
    fun missingAccountIsRejected() {
        assertEquals(RecurringEditError.ACCOUNT_REQUIRED, stateOf(accountId = null).validationError())
    }

    @Test
    fun transferWithoutTargetIsRejected() {
        val state = stateOf(txnType = TransactionType.TRANSFER, toAccountId = null)
        assertEquals(RecurringEditError.TARGET_REQUIRED, state.validationError())
    }

    @Test
    fun transferWithTargetPasses() {
        val state = stateOf(txnType = TransactionType.TRANSFER, toAccountId = "bank")
        assertNull(state.validationError())
    }

    @Test
    fun invalidIntervalIsRejected() {
        assertEquals(RecurringEditError.INVALID_INTERVAL, stateOf(interval = "0").validationError())
        assertEquals(RecurringEditError.INVALID_INTERVAL, stateOf(interval = "x").validationError())
    }

    @Test
    fun untilDateWithoutDateIsRejected() {
        val state = stateOf(endType = RecurrenceEndType.UNTIL_DATE, endDate = null)
        assertEquals(RecurringEditError.INVALID_END, state.validationError())
    }

    @Test
    fun countWithoutMaxOccurrencesIsRejected() {
        val state = stateOf(endType = RecurrenceEndType.COUNT, maxOccurrences = "")
        assertEquals(RecurringEditError.INVALID_END, state.validationError())
    }

    @Test
    fun countWithMaxOccurrencesPasses() {
        val state = stateOf(endType = RecurrenceEndType.COUNT, maxOccurrences = "12")
        assertNull(state.validationError())
    }

    @Test
    fun categoryChoicesFollowTransactionDirection() {
        val state = stateOf(txnType = TransactionType.EXPENSE).copy(
            categoryNodes = listOf(
                CategoryNode(
                    category = category("expense", CategoryKind.EXPENSE),
                    children = listOf(category("food", CategoryKind.EXPENSE, parentId = "expense")),
                ),
                CategoryNode(category = category("income", CategoryKind.INCOME)),
                CategoryNode(category = category("transfer", CategoryKind.TRANSFER)),
            ),
        )
        assertEquals(listOf("expense", "food"), state.categoryChoices().map { it.id })
    }

    @Test
    fun transferUsesTransferCategories() {
        val state = stateOf(
            txnType = TransactionType.TRANSFER,
            toAccountId = "bank",
        ).copy(
            categoryNodes = listOf(
                CategoryNode(category = category("expense", CategoryKind.EXPENSE)),
                CategoryNode(category = category("transfer", CategoryKind.TRANSFER)),
            ),
        )
        assertEquals(listOf("transfer"), state.categoryChoices().map { it.id })
    }

    @Test
    fun fieldVisibilityFollowsFrequency() {
        val daily = stateOf(freq = RecurrenceFrequency.DAILY)
        assertFalse(daily.showsIntervalField())
        assertFalse(daily.showsWeekdayField())
        assertFalse(daily.showsMonthDayField())
        assertFalse(daily.showsMonthField())

        val weekly = stateOf(freq = RecurrenceFrequency.WEEKLY)
        assertTrue(weekly.showsIntervalField())
        assertTrue(weekly.showsWeekdayField())
        assertFalse(weekly.showsMonthDayField())

        val monthly = stateOf(freq = RecurrenceFrequency.MONTHLY)
        assertTrue(monthly.showsMonthDayField())
        assertFalse(monthly.showsMonthField())

        val everyNMonths = stateOf(freq = RecurrenceFrequency.EVERY_N_MONTHS)
        assertTrue(everyNMonths.showsMonthDayField())
        assertFalse(everyNMonths.showsMonthField())

        val yearly = stateOf(freq = RecurrenceFrequency.YEARLY)
        assertTrue(yearly.showsMonthDayField())
        assertTrue(yearly.showsMonthField())
    }

    @Test
    fun intervalTrimsWhitespace() {
        assertEquals(3, stateOf(interval = " 3 ").intervalValue())
        assertNull(stateOf(interval = " 1.5 ").intervalValue())
    }

    private companion object {
        const val LEDGER_ID = "l1"
    }
}
