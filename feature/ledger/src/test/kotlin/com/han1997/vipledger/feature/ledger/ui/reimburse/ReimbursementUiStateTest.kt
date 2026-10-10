package com.han1997.vipledger.feature.ledger.ui.reimburse

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Reimbursement
import com.han1997.vipledger.core.domain.model.ReimbursementProgress
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReimbursementUiStateTest {
    private fun account(id: String, archived: Boolean = false, deleted: Boolean = false) = Account(
        id = id,
        ledgerId = "l1",
        name = id,
        type = AccountType.CASH,
        isArchived = archived,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = if (deleted) 1 else null,
    )

    private fun expense(id: String, cents: Long) = Txn(
        id = id,
        ledgerId = "l1",
        type = TransactionType.EXPENSE,
        amount = Money.ofCents(cents),
        accountId = "cash",
        occurredAt = 0,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun project(id: String, closed: Boolean = false, vararg items: Txn) = ReimbursementProgress(
        project = Reimbursement(id = id, ledgerId = "l1", title = id, closed = closed, createdAt = 0, updatedAt = 0),
        items = items.toList(),
        receipts = emptyList(),
    )

    @Test
    fun editorRequiresTitleAndAtLeastOneItem() {
        assertFalse(ReimbursementEditor(title = "", itemIds = setOf("a")).valid)
        assertFalse(ReimbursementEditor(title = "Trip", itemIds = emptySet()).valid)
        assertTrue(ReimbursementEditor(title = "Trip", itemIds = setOf("a")).valid)
    }

    @Test
    fun receiptFormAcceptsOnlyPositiveAmounts() {
        assertEquals(100050L, ReimbursementReceiptForm("p", amount = "1,000.50").money!!.cents)
        assertNull(ReimbursementReceiptForm("p", amount = "0").money)
        assertNull(ReimbursementReceiptForm("p", amount = "-5").money)
        assertNull(ReimbursementReceiptForm("p", amount = "abc").money)
    }

    @Test
    fun availableAccountsExcludeArchivedAndDeleted() {
        val state = ReimbursementUiState(accounts = listOf(account("cash"), account("old", archived = true), account("gone", deleted = true)))
        assertEquals(listOf("cash"), state.availableAccounts.map { it.id })
    }

    @Test
    fun pendingTotalCountsOnlyOpenProjects() {
        val state = ReimbursementUiState(
            projects = listOf(project("open", false, expense("a", 40_000)), project("closed", true, expense("b", 50_000))),
        )
        assertEquals(Money.ofCents(40_000), state.pendingTotal)
    }

    @Test
    fun candidatesHideItemsOwnedByOtherProjectsButKeepOwn() {
        val state = ReimbursementUiState(
            projects = listOf(project("p1", false, expense("a", 1_000)), project("p2", false, expense("b", 1_000))),
            expenses = listOf(expense("a", 1_000), expense("b", 1_000), expense("c", 1_000)),
        )
        assertEquals(listOf("a", "c"), state.candidatesFor("p1").map { it.id })
        assertEquals(listOf("b", "c"), state.candidatesFor("p2").map { it.id })
        assertEquals(listOf("c"), state.candidatesFor(null).map { it.id })
    }
}
