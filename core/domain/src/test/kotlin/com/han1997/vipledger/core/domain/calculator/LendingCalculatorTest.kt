package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.LendingCalculator
import com.han1997.vipledger.core.domain.model.LendingLink
import com.han1997.vipledger.core.domain.model.LendingProgress
import com.han1997.vipledger.core.domain.model.LendingRecord
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.LendingLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LendingCalculatorTest {
    private fun record(direction: LendingDirection = LendingDirection.LEND, cents: Long = 100_000, closed: Boolean = false) =
        LendingRecord(
            id = "lending",
            ledgerId = "l1",
            direction = direction,
            counterparty = "张三",
            principal = Money.ofCents(cents),
            accountId = "receivable",
            closed = closed,
            createdAt = 0,
            updatedAt = 0,
        )

    private fun link(id: String, kind: LendingLinkKind) = LendingLink(id, "lending", kind, createdAt = 0)

    private fun transfer(id: String, cents: Long) = Txn(
        id = id,
        ledgerId = "l1",
        type = TransactionType.TRANSFER,
        amount = Money.ofCents(cents),
        accountId = "cash",
        toAccountId = "receivable",
        occurredAt = 0,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun interest(id: String, cents: Long) = Txn(
        id = id,
        ledgerId = "l1",
        type = TransactionType.INCOME,
        amount = Money.ofCents(cents),
        accountId = "cash",
        occurredAt = 0,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun outstandingIsPrincipalMinusSettledWithFloorAtZero() {
        val links = listOf(link("disburse", LendingLinkKind.DISBURSE), link("s1", LendingLinkKind.SETTLE))
        val txns = listOf(transfer("disburse", 100_000), transfer("s1", 40_000), interest("i", 5_000))
        assertEquals(Money.ofCents(60_000), LendingCalculator.outstanding(record(), links, txns))
        assertEquals(
            Money.ZERO,
            LendingCalculator.outstanding(record(), links, listOf(transfer("disburse", 100_000), transfer("s1", 130_000))),
        )
    }

    @Test
    fun interestIsSummedSeparatelyAndDoesNotReducePrincipal() {
        val links = listOf(link("disburse", LendingLinkKind.DISBURSE), link("i1", LendingLinkKind.INTEREST), link("i2", LendingLinkKind.INTEREST))
        val txns = listOf(transfer("disburse", 100_000), interest("i1", 3_000), interest("i2", 2_000))
        val progress = LendingProgress(record(), links, txns)
        assertEquals(Money.ofCents(5_000), progress.interestTotal)
        assertEquals(Money.ofCents(100_000), progress.outstanding)
    }

    @Test
    fun settledAndActiveReflectOutstandingAndManualClose() {
        val settledTxns = listOf(transfer("s1", 100_000))
        val settled = LendingProgress(record(), listOf(link("s1", LendingLinkKind.SETTLE)), settledTxns)
        assertTrue(settled.settled)
        assertFalse(settled.active)
        val closed = LendingProgress(record(closed = true), emptyList(), emptyList())
        assertFalse(closed.settled)
        assertFalse(closed.active)
        assertTrue(LendingProgress(record(), emptyList(), emptyList()).active)
    }

    @Test
    fun linksReferencingMissingTxnsAreIgnored() {
        val links = listOf(link("s1", LendingLinkKind.SETTLE), link("ghost", LendingLinkKind.SETTLE))
        assertEquals(
            Money.ofCents(70_000),
            LendingCalculator.outstanding(record(), links, listOf(transfer("s1", 30_000))),
        )
    }

    @Test
    fun canSettleRequiresPositiveAmountWithinOutstanding() {
        val links = listOf(link("s1", LendingLinkKind.SETTLE))
        val txns = listOf(transfer("s1", 40_000))
        val target = record()
        assertTrue(LendingCalculator.canSettle(target, links, txns, Money.ofCents(60_000)))
        assertTrue(LendingCalculator.canSettle(target, links, txns, Money.ofCents(1)))
        assertFalse(LendingCalculator.canSettle(target, links, txns, Money.ZERO))
        assertFalse(LendingCalculator.canSettle(target, links, txns, Money.ofCents(-1)))
        assertFalse(LendingCalculator.canSettle(target, links, txns, Money.ofCents(60_001)))
        assertFalse(LendingCalculator.canSettle(target, links, txns, Money.ofCents(200_000)))
    }
}
