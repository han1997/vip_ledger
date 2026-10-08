package com.han1997.vipledger.autobook

import com.han1997.vipledger.core.domain.autobook.PaymentApp
import org.junit.Assert.*
import org.junit.Test

class PaymentWindowSessionTest {
    @Test fun repeatedStateEventsAndContentUpdatesKeepSameCaptureIdentity() {
        val state = PaymentWindowSession()
        state.update(PaymentApp.ALIPAY, 7, "BillActivity")
        val id = state.id
        assertFalse(state.update(PaymentApp.ALIPAY, 7, "BillActivity"))
        assertFalse(state.update(PaymentApp.ALIPAY, 7, null))
        assertEquals(id, state.id)
    }

    @Test fun navigationWindowChangesAndOtherAppsStartNewSessions() {
        val state = PaymentWindowSession()
        state.update(PaymentApp.ALIPAY, 7, "BillActivity")
        var old = state.id
        assertTrue(state.update(PaymentApp.ALIPAY, 7, "ListActivity"))
        assertNotEquals(old, state.id)
        old = state.id
        assertTrue(state.update(PaymentApp.ALIPAY, 8, "ListActivity"))
        assertNotEquals(old, state.id)
        old = state.id
        assertTrue(state.update(PaymentApp.WECHAT, 8, null))
        assertNotEquals(old, state.id)
    }
}
