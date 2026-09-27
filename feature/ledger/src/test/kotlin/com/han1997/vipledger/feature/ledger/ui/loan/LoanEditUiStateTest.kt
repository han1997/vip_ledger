package com.han1997.vipledger.feature.ledger.ui.loan

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

/** 新建分期表单的纯函数：金额 / 利率解析与预检。 */
class LoanEditUiStateTest {

    private fun stateOf(
        name: String = "花呗 12 期",
        accountId: String? = "card",
        method: InstallmentMethod = InstallmentMethod.EQUAL_INSTALLMENT,
        principal: String = "12000",
        periods: String = "12",
        annualRate: String = "18",
        upfrontFee: String = "",
        perPeriodFee: String = "",
        customTotals: String = "",
        dueDay: String = "",
    ) = LoanEditUiState(
        isLoading = false,
        ledgerId = LEDGER_ID,
        name = name,
        accountId = accountId,
        method = method,
        principalInput = principal,
        periodsInput = periods,
        annualRateInput = annualRate,
        upfrontFeeInput = upfrontFee,
        perPeriodFeeInput = perPeriodFee,
        customTotalsInput = customTotals,
        firstDueDate = LocalDate(2026, 2, 10),
        dueDayInput = dueDay,
    )

    @Test
    fun validStateHasNoError() {
        assertNull(stateOf().validationError())
    }

    @Test
    fun blankNameIsRejected() {
        assertEquals(LoanEditError.NAME_REQUIRED, stateOf(name = " ").validationError())
    }

    @Test
    fun missingAccountIsRejected() {
        assertEquals(LoanEditError.ACCOUNT_REQUIRED, stateOf(accountId = null).validationError())
    }

    @Test
    fun principalMustBePositive() {
        assertEquals(LoanEditError.INVALID_PRINCIPAL, stateOf(principal = "0").validationError())
        assertEquals(LoanEditError.INVALID_PRINCIPAL, stateOf(principal = "x").validationError())
    }

    @Test
    fun periodsMustBePositiveInteger() {
        assertEquals(LoanEditError.INVALID_PERIODS, stateOf(periods = "0").validationError())
        assertEquals(LoanEditError.INVALID_PERIODS, stateOf(periods = "3.5").validationError())
    }

    @Test
    fun negativeRateIsRejected() {
        assertEquals(LoanEditError.INVALID_RATE, stateOf(annualRate = "-1").validationError())
    }

    @Test
    fun blankRateMeansZero() {
        val state = stateOf(annualRate = "")
        assertNull(state.validationError())
        assertEquals(0, state.annualRateBpValue())
    }

    @Test
    fun ratePercentConvertsToBasisPoints() {
        assertEquals(1800, stateOf(annualRate = "18").annualRateBpValue())
        assertEquals(1850, stateOf(annualRate = "18.5").annualRateBpValue())
    }

    @Test
    fun negativeFeeIsRejected() {
        assertEquals(LoanEditError.INVALID_FEE, stateOf(perPeriodFee = "-5").validationError())
    }

    @Test
    fun dueDayMustBeWithinOneToTwentyEight() {
        assertEquals(LoanEditError.INVALID_DUE_DAY, stateOf(dueDay = "29").validationError())
        assertNull(stateOf(dueDay = "28").validationError())
    }

    @Test
    fun customMethodRequiresOneAmountPerPeriod() {
        val wrong = stateOf(
            method = InstallmentMethod.CUSTOM,
            periods = "3",
            customTotals = "1000, 1000",
        )
        assertEquals(LoanEditError.INVALID_CUSTOM, wrong.validationError())

        val right = stateOf(
            method = InstallmentMethod.CUSTOM,
            periods = "3",
            customTotals = "1000 1000 1000",
        )
        assertNull(right.validationError())
        assertEquals(3, right.customTotalsValue()?.size)
        assertEquals(Money.parse("1000"), right.customTotalsValue()?.first())
    }

    @Test
    fun customTotalsAreIgnoredForOtherMethods() {
        val state = stateOf(method = InstallmentMethod.EQUAL_PRINCIPAL, customTotals = "")
        assertNull(state.validationError())
    }

    @Test
    fun onlyDebtAccountsCanBeChosen() {
        val state = stateOf().copy(
            accounts = listOf(
                account("cash", AccountType.CASH),
                account("card", AccountType.CREDIT_CARD),
                account("loan", AccountType.LOAN),
            ),
        )
        assertEquals(listOf("card", "loan"), state.debtAccounts.map { it.id })
    }

    private fun account(id: String, type: AccountType) = Account(
        id = id,
        ledgerId = LEDGER_ID,
        name = id,
        type = type,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        const val LEDGER_ID = "l1"
    }
}
