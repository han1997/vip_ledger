package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceCalculatorTest {

    private val cash = Account(
        id = "cash",
        ledgerId = "l1",
        name = "现金",
        type = AccountType.CASH,
        initialBalance = Money.ofCents(10_000),
        currentBalance = Money.ofCents(10_000),
        createdAt = 0,
        updatedAt = 0,
    )
    private val card = Account(
        id = "card",
        ledgerId = "l1",
        name = "信用卡",
        type = AccountType.CREDIT_CARD,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun txn(
        id: String = "t1",
        type: TransactionType,
        amountCents: Long,
        accountId: String = "cash",
        toAccountId: String? = null,
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
        deletedAt: Long? = null,
    ) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = accountId,
        toAccountId = toAccountId,
        occurredAt = 0,
        draftStatus = draftStatus,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = deletedAt,
    )

    @Test
    fun expenseAndIncomeMoveTheSourceAccountOnly() {
        val txns = listOf(
            txn(type = TransactionType.EXPENSE, amountCents = 3_000),
            txn(id = "t2", type = TransactionType.INCOME, amountCents = 20_000),
        )
        assertEquals(Money.ofCents(10_000 - 3_000 + 20_000), BalanceCalculator.balanceOf(cash, txns))
        assertEquals(Money.ZERO, BalanceCalculator.balanceOf(card, txns))
        assertNull(BalanceCalculator.deltaOf(txns.first(), "card"))
    }

    @Test
    fun transferMovesValueBetweenBothAccounts() {
        val transfer = txn(type = TransactionType.TRANSFER, amountCents = 1_000, accountId = "cash", toAccountId = "card")
        assertEquals(Money.ofCents(-1_000), BalanceCalculator.deltaOf(transfer, "cash"))
        assertEquals(Money.ofCents(1_000), BalanceCalculator.deltaOf(transfer, "card"))
    }

    @Test
    fun creditCardSpendingGoesNegativeAndRepaymentBringsItBack() {
        val spending = txn(type = TransactionType.EXPENSE, amountCents = 50_000, accountId = "card")
        val repay = txn(id = "t2", type = TransactionType.DEBT_REPAY, amountCents = 50_000, accountId = "cash", toAccountId = "card")
        val txns = listOf(spending, repay)
        assertEquals(Money.ZERO, BalanceCalculator.balanceOf(card, txns))
        assertEquals(Money.ofCents(10_000 - 50_000), BalanceCalculator.balanceOf(cash, txns))
        assertEquals(Money.ofCents(50_000), BalanceCalculator.totalDebt(listOf(card), listOf(spending)))
        assertEquals(Money.ZERO, BalanceCalculator.totalDebt(listOf(card), txns))
    }

    @Test
    fun adjustUsesItsOwnSignAndDoesNotNeedATarget() {
        val increase = txn(type = TransactionType.ADJUST, amountCents = 5_000)
        val decrease = txn(id = "t2", type = TransactionType.ADJUST, amountCents = -2_000)
        assertEquals(Money.ofCents(15_000), BalanceCalculator.balanceOf(cash, listOf(increase)))
        assertEquals(Money.ofCents(8_000), BalanceCalculator.balanceOf(cash, listOf(decrease)))
    }

    @Test
    fun draftsAndDeletedTxnsDoNotAffectBalances() {
        val draft = txn(type = TransactionType.EXPENSE, amountCents = 1_000, draftStatus = DraftStatus.PENDING)
        val deleted = txn(id = "t2", type = TransactionType.EXPENSE, amountCents = 1_000, deletedAt = 1)
        val discarded = txn(id = "t3", type = TransactionType.EXPENSE, amountCents = 1_000, draftStatus = DraftStatus.DISCARDED)
        assertEquals(Money.ofCents(10_000), BalanceCalculator.balanceOf(cash, listOf(draft, deleted, discarded)))
    }

    @Test
    fun netWorthCombinesAssetsAndDebtsAndSkipsArchivedAccounts() {
        val spending = txn(type = TransactionType.EXPENSE, amountCents = 50_000, accountId = "card")
        val txns = listOf(spending)
        assertEquals(Money.ofCents(10_000 - 50_000), BalanceCalculator.netWorth(listOf(cash, card), txns))
        assertEquals(Money.ofCents(10_000), BalanceCalculator.netWorth(listOf(cash, card.copy(includeInNetWorth = false)), txns))
        assertEquals(Money.ofCents(10_000), BalanceCalculator.netWorth(listOf(cash, card.copy(isArchived = true)), txns))
    }
}