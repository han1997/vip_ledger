package com.han1997.vipledger.autobook

import com.han1997.vipledger.core.domain.autobook.PaymentApp
import java.util.UUID

/** Repeated window-state events for the same container are one capture session. */
class PaymentWindowSession {
    var id: String = UUID.randomUUID().toString(); private set
    var app: PaymentApp? = null; private set
    var windowId: Int = -1; private set
    private var container: String? = null

    fun update(app: PaymentApp, windowId: Int, container: String?): Boolean {
        val changedWindow = this.app != app || this.windowId != windowId
        val changed = changedWindow || (container != null && container != this.container)
        if (changed) id = UUID.randomUUID().toString()
        if (changedWindow) this.container = null
        this.app = app
        this.windowId = windowId
        if (container != null) this.container = container
        return changed
    }
}
