package com.han1997.vipledger.feature.ledger.ui.wealth

import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import kotlin.test.*

class GoalEditorTest {
    @Test fun goalRequiresPositiveTargetAndValidOptionalDate() {
        val editor = GoalEditor(name = "Trip", target = "1,000.50", accountId = "a")
        assertTrue(editor.valid); assertEquals(100050L, editor.amount!!.cents)
        assertFalse(editor.copy(target = "0").valid)
        assertFalse(editor.copy(name = " ").valid)
        assertFalse(editor.copy(deadline = "2026-02-30").valid)
        assertTrue(editor.copy(deadline = "2028-02-29").valid)
    }
    @Test fun accountTransferAndEditorRoutesCarryTheRequestedAccount() {
        assertEquals("ledger/txn/edit/l?txnId=&date=&accountId=a&entryType=TRANSFER", LedgerRoutes.txnEdit("l", accountId = "a"))
        assertEquals("ledger/manage/accounts/l?editAccountId=a", LedgerRoutes.accountManage("l", "a"))
    }
}
