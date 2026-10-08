package com.han1997.vipledger.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerEnumsTest {

    @Test
    fun onlyIncomeAndExpenseCountForStats() {
        assertTrue(TransactionType.EXPENSE.isAccountingForStats)
        assertTrue(TransactionType.INCOME.isAccountingForStats)
        assertTrue(TransactionType.REFUND.isAccountingForStats)
        assertFalse(TransactionType.TRANSFER.isAccountingForStats)
        assertFalse(TransactionType.DEBT_REPAY.isAccountingForStats)
        assertFalse(TransactionType.ADJUST.isAccountingForStats)
    }

    @Test
    fun transferLikeTypesNeedTargetAccount() {
        assertTrue(TransactionType.TRANSFER.requiresTargetAccount)
        assertTrue(TransactionType.DEBT_REPAY.requiresTargetAccount)
        assertFalse(TransactionType.EXPENSE.requiresTargetAccount)
        assertFalse(TransactionType.INCOME.requiresTargetAccount)
    }

    @Test
    fun debtAccountsAreFlagged() {
        assertTrue(AccountType.CREDIT_CARD.isDebt)
        assertTrue(AccountType.PAYABLE.isDebt)
        assertTrue(AccountType.LOAN.isDebt)
        assertFalse(AccountType.CASH.isDebt)
        assertFalse(AccountType.BANK_DEBIT.isDebt)
        assertFalse(AccountType.VIRTUAL.isDebt)
        assertFalse(AccountType.RECEIVABLE.isDebt)
    }

    @Test
    fun billingCycleOnlyForCreditAndLoan() {
        assertTrue(AccountType.CREDIT_CARD.hasBillingCycle)
        assertTrue(AccountType.LOAN.hasBillingCycle)
        assertFalse(AccountType.CASH.hasBillingCycle)
    }
}
