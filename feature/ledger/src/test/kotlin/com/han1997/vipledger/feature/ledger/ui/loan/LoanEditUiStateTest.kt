package com.han1997.vipledger.feature.ledger.ui.loan

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentOrigin
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        origin: InstallmentOrigin = InstallmentOrigin.EXISTING,
        disbursementAccountId: String? = null,
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
        origin = origin,
        disbursementAccountId = disbursementAccountId,
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

    @Test
    fun disbursementRequiresAReceivingCashAccount() {
        val missing = stateOf(origin = InstallmentOrigin.DISBURSEMENT)
        assertEquals(LoanEditError.DISBURSEMENT_ACCOUNT_REQUIRED, missing.validationError())

        val chosen = stateOf(origin = InstallmentOrigin.DISBURSEMENT, disbursementAccountId = "cash")
        assertNull(chosen.validationError())
        // 已有负债不需要到账账户
        assertNull(stateOf(origin = InstallmentOrigin.EXISTING).validationError())
    }

    @Test
    fun disbursementAccountsExcludeTheDebtAccountItself() {
        val state = stateOf().copy(
            accounts = listOf(
                account("cash", AccountType.CASH),
                account("card", AccountType.CREDIT_CARD),
            ),
        )
        assertEquals(listOf("cash"), state.disbursementAccounts.map { it.id })
    }

    @Test
    fun customAmountsPerPeriodAreParsedFromMultipleLines() {
        val state = stateOf(
            method = InstallmentMethod.CUSTOM,
            principal = "12000",
            periods = "2",
            customTotals = "6000,300,100\n6000,200,0",
        )
        val amounts = assertNotNull(state.customAmountsValue())
        assertEquals(2, amounts.size)
        assertEquals(Money.parse("6000"), amounts.first().principal)
        assertEquals(Money.parse("300"), amounts.first().interest)
        assertEquals(Money.parse("100"), amounts.first().fee)
        assertNull(state.validationError())
    }

    @Test
    fun customAmountsMustSumToThePlanPrincipal() {
        val state = stateOf(
            method = InstallmentMethod.CUSTOM,
            principal = "12000",
            periods = "2",
            customTotals = "6000,300,100\n5900,200,0",
        )
        assertEquals(LoanEditError.INVALID_CUSTOM, state.validationError())
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
