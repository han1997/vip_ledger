package com.han1997.vipledger.feature.ledger.ui.investment

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Investment
import com.han1997.vipledger.core.domain.model.InvestmentLink
import com.han1997.vipledger.core.domain.model.InvestmentProgress
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 理财页的纯函数：表单校验、可见列表与汇总。 */
class InvestmentUiStateTest {

    private fun account(id: String, name: String, type: AccountType) = Account(
        id = id,
        ledgerId = "l1",
        name = name,
        type = type,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun txn(id: String, amount: Long, type: TransactionType) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amount),
        accountId = "cash",
        occurredAt = 1,
        title = id,
        draftStatus = DraftStatus.CONFIRMED,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun progress(
        id: String,
        closed: Boolean = false,
        invested: Long = 100_000,
        redeemed: Long = 0,
        gain: Long = 0,
        loss: Long = 0,
    ): InvestmentProgress {
        val record = Investment(
            id = id,
            ledgerId = "l1",
            name = id,
            accountId = "inv-$id",
            closed = closed,
            createdAt = 0,
            updatedAt = 0,
        )
        val links = buildList {
            add(InvestmentLink("$id-in", id, InvestmentLinkKind.INVEST, 1))
            if (redeemed > 0) add(InvestmentLink("$id-out", id, InvestmentLinkKind.REDEEM, 1))
            if (gain > 0) add(InvestmentLink("$id-gain", id, InvestmentLinkKind.INCOME, 1))
            if (loss > 0) add(InvestmentLink("$id-loss", id, InvestmentLinkKind.EXPENSE, 1))
        }
        val txns = buildList {
            add(txn("$id-in", invested, TransactionType.TRANSFER))
            if (redeemed > 0) add(txn("$id-out", redeemed, TransactionType.TRANSFER))
            if (gain > 0) add(txn("$id-gain", gain, TransactionType.INCOME))
            if (loss > 0) add(txn("$id-loss", loss, TransactionType.EXPENSE))
        }
        return InvestmentProgress(record, links, txns)
    }

    @Test
    fun editorRequiresANameAPositiveAmountAndAnAccount() {
        assertTrue(InvestmentEditor(name = "基金", cashAccountId = "cash", amount = "1000").valid)
        assertFalse(InvestmentEditor(name = " ", cashAccountId = "cash", amount = "1000").valid)
        assertFalse(InvestmentEditor(name = "基金", cashAccountId = "", amount = "1000").valid)
        assertFalse(InvestmentEditor(name = "基金", cashAccountId = "cash", amount = "0").valid)
        assertFalse(InvestmentEditor(name = "基金", cashAccountId = "cash", amount = "x").valid)
        assertNull(InvestmentEditor(name = "基金", cashAccountId = "cash", amount = "x").money)
    }

    @Test
    fun availableAccountsExcludeDebtAccounts() {
        val state = InvestmentUiState(
            loading = false,
            accounts = listOf(
                account("cash", "现金", AccountType.CASH),
                account("card", "信用卡", AccountType.CREDIT_CARD),
                account("inv", "投资", AccountType.INVESTMENT),
            ),
        )
        assertEquals(listOf("cash", "inv"), state.availableAccounts.map { it.id })
    }

    @Test
    fun closedAndEmptiedPositionsAreHiddenUnlessRequested() {
        val active = progress("active", invested = 100_000, gain = 5_000, loss = 2_000)
        val closed = progress("closed", closed = true)
        val emptied = progress("emptied", invested = 100_000, redeemed = 100_000)

        val hidden = InvestmentUiState(loading = false, investments = listOf(active, closed, emptied))
        assertEquals(listOf("active"), hidden.visible.map { it.investment.id })
        assertEquals(Money.ofCents(100_000), hidden.outstandingTotal)
        assertEquals(Money.ofCents(3_000), hidden.realizedTotal)

        val shown = hidden.copy(showClosed = true)
        assertEquals(listOf("active", "closed", "emptied"), shown.visible.map { it.investment.id })
    }

    @Test
    fun operationAcceptsOnlyPositiveAmounts() {
        val form = InvestmentOperation(
            investmentId = "inv",
            kind = InvestmentLinkKind.REDEEM,
            cashAccountId = "cash",
            amount = "500",
        )
        assertEquals(Money.parse("500"), form.money)
        assertNull(form.copy(amount = "").money)
        assertNull(form.copy(amount = "-1").money)
    }
}