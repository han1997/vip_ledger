package com.han1997.vipledger.autobook

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.han1997.vipledger.core.domain.autobook.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class PaymentNotificationService : NotificationListenerService() {
    @Inject lateinit var processor: PaymentCaptureProcessor
    @Inject lateinit var control: AndroidAutoCaptureControl
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onListenerConnected() { control.update { it.copy(notificationConnected = true, issue = null) } }
    override fun onListenerDisconnected() {
        control.update { it.copy(notificationConnected = false, issue = RuntimeIssue.DISCONNECTED) }
        requestRebind(ComponentName(this, PaymentNotificationService::class.java))
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = PaymentApp.fromPackage(sbn.packageName) ?: return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        // A regular chat mentioning “支付成功” is not a payment notification.
        if (app == PaymentApp.WECHAT && title !in setOf("微信支付", "微信收款助手")) return
        val text = listOf(title, extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()).joinToString("\n")
        if (!PaymentParser.isPaymentPage(text)) return
        scope.launch { processor.submit(PaymentObservation(app, CaptureSource.NOTIFICATION,
            "${sbn.key}:${sbn.postTime}", sbn.postTime, text)) }
    }
    override fun onDestroy() {
        control.update { it.copy(notificationConnected = false, issue = RuntimeIssue.DISCONNECTED) }
        scope.cancel(); super.onDestroy()
    }
}
