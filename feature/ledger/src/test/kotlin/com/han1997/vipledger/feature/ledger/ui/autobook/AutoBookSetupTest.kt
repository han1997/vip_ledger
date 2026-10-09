package com.han1997.vipledger.feature.ledger.ui.autobook

import com.han1997.vipledger.core.domain.autobook.*
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.*

class AutoBookSetupTest {
    private val ledger = Ledger("ledger", "Test", createdAt = 1, updatedAt = 1)
    private val account = Account("account", ledger.id, "Wallet", AccountType.VIRTUAL, createdAt = 1, updatedAt = 1)
    private val ready = AutoBookUiState(config = AutoBookConfig(ledgerId = ledger.id), ledgers = listOf(ledger),
        accounts = listOf(account), bindingToken = "零钱", bindingAccount = account.id)

    @Test fun selectingLedgerEnablesSetupButAccountMustBelongToThatLedger() {
        assertFalse(ready.copy(config = AutoBookConfig()).hasUsableLedger)
        assertTrue(ready.hasUsableLedger)
        assertTrue(ready.canBind)
        assertFalse(ready.copy(accounts = emptyList()).canBind)
        assertFalse(ready.copy(accounts = listOf(account.copy(ledgerId = "other"))).canBind)
        assertFalse(ready.copy(accounts = listOf(account.copy(isArchived = true))).canBind)
        assertFalse(ready.copy(bindingToken = " ").canBind)
        assertFalse(ready.copy(busy = true).canBind)
        assertFalse(ready.copy(ledgers = listOf(ledger.copy(archivedAt = 2))).hasUsableLedger)
    }

    @Test fun paymentChoicesUseActualRecordsFromSelectedAppAndLedger() {
        val facts = PaymentFacts(TransactionType.EXPENSE, 100, "shop", "零钱", null, null, null, true, ReviewReason.ACCOUNT)
        val candidate = AutoCandidate("one", ledger.id, PaymentApp.WECHAT, CaptureSource.NOTIFICATION, 1, facts)
        val state = ready.copy(candidates = listOf(candidate, candidate.copy(id = "repeat"),
            candidate.copy(id = "other-app", app = PaymentApp.ALIPAY, facts = facts.copy(accountToken = "余额")),
            candidate.copy(id = "other-ledger", ledgerId = "other", facts = facts.copy(accountToken = "card")),
            candidate.copy(id = "unknown", facts = facts.copy(accountToken = null))))
        assertEquals(listOf("零钱"), state.bindingTokens)
        assertEquals(listOf("余额"), state.copy(bindingApp = PaymentApp.ALIPAY).bindingTokens)
    }
}
