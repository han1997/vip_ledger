package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.model.*
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.*

class WealthCalculatorTest {
    private fun account(id: String, amount: Long, type: AccountType = AccountType.CASH) = Account(id, "l", id, type, currentBalance = Money.ofCents(amount), createdAt = 0, updatedAt = 0)
    @Test fun totalsUseExactlyTheSameIncludedAccountSet() {
        val rows = listOf(account("cash", 10000), account("overdraft", -2000), account("credit-positive", 500, AccountType.CREDIT_CARD), account("debt", -3000, AccountType.LOAN), account("excluded", -8000).copy(includeInNetWorth = false), account("archived", 9000).copy(isArchived = true), account("deleted", 100).copy(deletedAt = 1))
        assertEquals(10500L, BalanceCalculator.totalAssetsFromCache(rows).cents)
        assertEquals(5000L, BalanceCalculator.totalDebtFromCache(rows).cents)
        assertEquals(BalanceCalculator.totalAssetsFromCache(rows) - BalanceCalculator.totalDebtFromCache(rows), BalanceCalculator.netWorthFromCache(rows))
        assertEquals(AssetGroup.PREPAID, AssetGroup.of(AccountType.PREPAID))
        assertFalse(AccountType.PREPAID.isDebt)
        assertFailsWith<ArithmeticException> { BalanceCalculator.totalDebtFromCache(listOf(account("overflow", Long.MIN_VALUE))) }
    }
    @Test fun progressUsesOnlyLinkedConfirmedTransfersAndPreservesDirection() {
        val goal = SavingsGoal("g", "l", "stash", "Trip", Money.ofCents(10000), createdAt = 0, updatedAt = 0)
        val deposit = Txn("d", "l", TransactionType.TRANSFER, Money.ofCents(20000), "cash", "stash", occurredAt = 0, createdAt = 0, updatedAt = 0)
        val withdrawal = deposit.copy(id = "w", amount = Money.ofCents(5000), accountId = "stash", toAccountId = "cash")
        val unlinked = deposit.copy(id = "ignored", amount = Money.ofCents(999999))
        val deleted = deposit.copy(id = "deleted", deletedAt = 1)
        val draft = deposit.copy(id = "draft", draftStatus = DraftStatus.PENDING)
        val wrongLedger = deposit.copy(id = "wrong", ledgerId = "other")
        val entries = listOf(deposit, withdrawal, unlinked, deleted, draft, wrongLedger)
        val links = entries.filter { it.id != "ignored" }.map { SavingsLink(it.id, "g", 0) }
        val result = SavingsCalculator.progress(goal, links, entries)
        assertEquals(15000L, result.saved.cents); assertTrue(result.achieved); assertEquals(1f, result.fraction)
        assertTrue(result.hasLinks)
        val deletedOnly = SavingsCalculator.progress(goal, links, entries.map { it.copy(deletedAt = 1) })
        assertEquals(0L, deletedOnly.saved.cents); assertTrue(deletedOnly.hasLinks)
        assertEquals(0, SavingsCalculator.direction(goal, deposit.copy(type = TransactionType.EXPENSE)))
        assertEquals(0, SavingsCalculator.direction(goal, deposit.copy(toAccountId = "cash")))
    }
    @Test fun layoutNormalizesRestoredRowsAndMovesWithoutLosingVisibility() {
        val defaults = HomeCardLayout.defaults()
        assertEquals(listOf(HomeCard.BUDGET, HomeCard.TODAY, HomeCard.SAVINGS, HomeCard.WEEK), defaults.filter { it.visible }.map { it.card })
        val restored = HomeCardLayout.normalize(listOf(HomeCardSetting(HomeCard.WEEK, 9, false), HomeCardSetting(HomeCard.WEEK, 0, true)))
        assertEquals(5, restored.size); assertEquals(HomeCard.WEEK, restored.first().card); assertFalse(restored.first().visible)
        val moved = HomeCardLayout.move(defaults, HomeCard.SAVINGS, -1)
        assertEquals(HomeCard.SAVINGS, moved[1].card)
        assertEquals((0..4).toList(), moved.map { it.position })
        assertEquals(defaults, HomeCardLayout.move(defaults, HomeCard.BUDGET, -1))
    }
}
