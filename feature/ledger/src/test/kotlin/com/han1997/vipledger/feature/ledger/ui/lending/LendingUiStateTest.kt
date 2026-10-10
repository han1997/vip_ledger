package com.han1997.vipledger.feature.ledger.ui.lending

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.LendingLink
import com.han1997.vipledger.core.domain.model.LendingProgress
import com.han1997.vipledger.core.domain.model.LendingRecord
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.LendingLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LendingUiStateTest {
    private fun account(id: String, type: AccountType = AccountType.CASH, archived: Boolean = false, deleted: Boolean = false) = Account(
        id = id,
        ledgerId = "l1",
        name = id,
        type = type,
        isArchived = archived,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = if (deleted) 1 else null,
    )

    private fun record(id: String, cents: Long = 100_000, closed: Boolean = false) = LendingRecord(
        id = id,
        ledgerId = "l1",
        direction = LendingDirection.LEND,
        counterparty = "张三",
        principal = Money.ofCents(cents),
        accountId = "receivable",
        closed = closed,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun settled(id: String, cents: Long) = LendingProgress(
        record = record(id),
        links = listOf(LendingLink("t$id", id, LendingLinkKind.SETTLE, 0)),
        txns = listOf(
            Txn("t$id", "l1", TransactionType.TRANSFER, Money.ofCents(cents), "receivable", toAccountId = "cash", occurredAt = 0, createdAt = 0, updatedAt = 0),
        ),
    )

    @Test
    fun editorRequiresCounterpartyPositivePrincipalAndDependsOnOptionalDate() {
        val editor = LendingEditor(counterparty = "张三", principal = "1,000", cashAccountId = "cash")
        assertTrue(editor.valid)
        assertEquals(Money.ofCents(100_000), editor.amount)
        assertNull(editor.date)
        assertFalse(editor.copy(counterparty = " ").valid)
        assertFalse(editor.copy(principal = "0").valid)
        assertFalse(editor.copy(cashAccountId = "").valid)
        assertTrue(editor.copy(dueDate = "2026-10-31").valid)
        assertEquals(LocalDate(2026, 10, 31), editor.copy(dueDate = "2026-10-31").date)
        assertFalse(editor.copy(dueDate = "2026-02-30").valid)
    }

    @Test
    fun settlementAcceptsOnlyPositiveAmounts() {
        assertEquals(5000L, LendingSettlement("l", interest = false, amount = "50").money!!.cents)
        assertNull(LendingSettlement("l", interest = false, amount = "0").money)
        assertNull(LendingSettlement("l", interest = true, amount = "-1").money)
    }

    @Test
    fun availableAccountsExcludeDebtArchivedAndDeleted() {
        val state = LendingUiState(
            accounts = listOf(
                account("cash"),
                account("card", AccountType.CREDIT_CARD),
                account("old", archived = true),
                account("gone", deleted = true),
            ),
        )
        assertEquals(listOf("cash"), state.availableAccounts.map { it.id })
    }

    @Test
    fun outstandingTotalCountsOnlyActiveRecords() {
        val state = LendingUiState(
            lendings = listOf(
                LendingProgress(record("open"), emptyList(), emptyList()),
                LendingProgress(record("closed", closed = true), emptyList(), emptyList()),
                settled("done", 100_000),
            ),
        )
        assertEquals(Money.ofCents(100_000), state.outstandingTotal)
    }
}
