package com.han1997.vipledger.feature.ledger.ui.autobook

import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.*

class CandidateFormTest {
    private val form = CandidateForm("id", TransactionType.EXPENSE, "12.34", "account", title = "Shop")
    @Test fun confirmationUsesMoneyParseContractAndRealAccount() {
        assertEquals(1234, form.confirmation()!!.amountCents)
        assertEquals(123456, form.copy(amount = "¥1,234.56").confirmation()!!.amountCents)
        assertEquals(800, form.copy(amount = "8").confirmation()!!.amountCents)
        assertEquals(1235, form.copy(amount = "12.345").confirmation()!!.amountCents)
        listOf("", "abc", "12元", "0", "-1", "99999999999999999999999").forEach {
            assertNull(form.copy(amount = it).confirmation())
        }
        assertNull(form.copy(accountId = "").confirmation())
    }
    @Test fun selfTransferCannotUseTheSameAccount() {
        assertNull(form.copy(type = TransactionType.TRANSFER, targetId = "account").confirmation())
        assertNotNull(form.copy(type = TransactionType.TRANSFER, targetId = "another").confirmation())
        assertNull(form.confirmation()!!.targetAccountId)
    }
}
