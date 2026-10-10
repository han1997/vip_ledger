package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceCacheTest {

    @Test
    fun netWorthSkipsArchivedDeletedAndExcludedAccounts() {
        val accounts = listOf(
            account("a1", AccountType.CASH, 100000),
            account("a2", AccountType.CREDIT_CARD, -30000),
            account("a3", AccountType.BANK_DEBIT, 9999, archived = true),
            account("a4", AccountType.VIRTUAL, 9999, deleted = true),
            account("a5", AccountType.INVESTMENT, 9999, includeInNetWorth = false),
        )

        assertEquals(Money.ofCents(70000), BalanceCalculator.netWorthFromCache(accounts))
    }

    @Test
    fun totalDebtCountsAllIncludedNegativeBalances() {
        val accounts = listOf(
            account("a1", AccountType.CREDIT_CARD, -30000),
            account("a2", AccountType.LOAN, -120000),
            account("a3", AccountType.PAYABLE, 5000),
            account("a4", AccountType.CASH, -8000),
        )

        assertEquals(Money.ofCents(158000), BalanceCalculator.totalDebtFromCache(accounts))
    }

    private fun account(
        id: String,
        type: AccountType,
        balanceCents: Long,
        archived: Boolean = false,
        deleted: Boolean = false,
        includeInNetWorth: Boolean = true,
    ) = Account(
        id = id,
        ledgerId = "l1",
        name = id,
        type = type,
        currentBalance = Money.ofCents(balanceCents),
        includeInNetWorth = includeInNetWorth,
        isArchived = archived,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = if (deleted) 1L else null,
    )
}
