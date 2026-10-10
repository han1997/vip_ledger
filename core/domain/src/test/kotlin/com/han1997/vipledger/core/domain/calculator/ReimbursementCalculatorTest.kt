package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Reimbursement
import com.han1997.vipledger.core.domain.model.ReimbursementCalculator
import com.han1997.vipledger.core.domain.model.ReimbursementProgress
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReimbursementCalculatorTest {
    private fun expense(id: String, cents: Long, categoryId: String? = "food", exclude: Boolean = false) = Txn(
        id = id,
        ledgerId = "l1",
        type = TransactionType.EXPENSE,
        amount = Money.ofCents(cents),
        accountId = "cash",
        categoryId = categoryId,
        occurredAt = 0,
        excludeFromStats = exclude,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun receipt(id: String, of: String?, cents: Long) = Txn(
        id = id,
        ledgerId = "l1",
        type = TransactionType.REFUND,
        amount = Money.ofCents(cents),
        accountId = "cash",
        occurredAt = 0,
        refundOfTxnId = of,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun project(id: String, closed: Boolean = false) = Reimbursement(
        id = id,
        ledgerId = "l1",
        title = id,
        closed = closed,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun remainingForCountsOnlyReceiptsOfTheSameItem() {
        val item = expense("cost", 100_000)
        val receipts = listOf(receipt("r1", "cost", 30_000), receipt("r2", "other", 20_000))
        assertEquals(Money.ofCents(70_000), ReimbursementCalculator.remainingFor(item, receipts))
        assertEquals(Money.ofCents(100_000), ReimbursementCalculator.remainingFor(item, emptyList()))
    }

    @Test
    fun remainingForGoesNegativeWhenReceiptsExceedTheItem() {
        val item = expense("cost", 100_000)
        val receipts = listOf(receipt("r1", "cost", 120_000))
        assertEquals(Money.ofCents(-20_000), ReimbursementCalculator.remainingFor(item, receipts))
    }

    @Test
    fun progressSumsClaimableReceivedAndPending() {
        val progress = ReimbursementProgress(
            project = project("p1"),
            items = listOf(expense("a", 60_000), expense("b", 40_000)),
            receipts = listOf(receipt("r1", "a", 25_000)),
        )
        assertEquals(Money.ofCents(100_000), progress.claimable)
        assertEquals(Money.ofCents(25_000), progress.received)
        assertEquals(Money.ofCents(75_000), progress.pending)
        assertFalse(progress.settled)
    }

    @Test
    fun fullyReceivedProjectIsSettledEvenWhenClosed() {
        val progress = ReimbursementProgress(
            project = project("p1", closed = true),
            items = listOf(expense("a", 100_000)),
            receipts = listOf(receipt("r1", "a", 100_000)),
        )
        assertTrue(progress.settled)
        assertEquals(Money.ZERO, progress.pending)
    }

    @Test
    fun pendingTotalCountsOnlyOpenProjects() {
        val open = ReimbursementProgress(project("open"), listOf(expense("a", 40_000)), emptyList())
        val closed = ReimbursementProgress(project("closed", closed = true), listOf(expense("b", 50_000)), emptyList())
        assertEquals(Money.ofCents(40_000), ReimbursementCalculator.pendingTotal(listOf(open, closed)))
        assertEquals(Money.ZERO, ReimbursementCalculator.pendingTotal(emptyList()))
    }
}
