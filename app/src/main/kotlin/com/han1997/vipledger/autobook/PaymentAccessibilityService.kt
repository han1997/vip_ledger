package com.han1997.vipledger.autobook

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.han1997.vipledger.core.data.autobook.PaymentOcr
import com.han1997.vipledger.core.domain.autobook.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {
    @Inject lateinit var processor: PaymentCaptureProcessor
    @Inject lateinit var repository: AutoBookRepository
    @Inject lateinit var control: AndroidAutoCaptureControl
    @Inject lateinit var ocr: PaymentOcr
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val captureSession = PaymentWindowSession()
    private val session get() = captureSession.id
    private val window get() = captureSession.windowId
    private var paymentActivity = false
    private var resultActivity = false
    private var retryableOrderOcr = false
    private val ocrThrottle = PaymentOcrThrottle()
    private var screenshotBusy = false
    private var screenshotSession: String? = null
    private var lastText: String? = null

    override fun onServiceConnected() { control.update { it.copy(accessibilityConnected = true, issue = null) } }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val app = PaymentApp.fromPackage(event.packageName?.toString().orEmpty()) ?: return
        if (captureSession.app != app) { paymentActivity = false; resultActivity = false; retryableOrderOcr = false }
        var container: String? = null
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val name = event.className?.toString().orEmpty()
            if (PaymentActivityClassifier.isTrackedContainer(name)) {
                container = name
                paymentActivity = PaymentActivityClassifier.isPaymentActivity(app, name)
                retryableOrderOcr = PaymentActivityClassifier.isVerifiedOrderOcrActivity(app, name)
                resultActivity = PaymentActivityClassifier.isResultActivity(name) || retryableOrderOcr
            }
        }
        if (captureSession.update(app, event.windowId, container)) lastText = null
        // Fail closed for chat / unadapted activities; never screenshot arbitrary app screens.
        if (!paymentActivity) return
        job?.cancel()
        job = scope.launch {
            delay(500)
            val config = repository.config()
            if (!config.enabled || app !in config.enabledApps) return@launch
            val root = rootInActiveWindow ?: return@launch
            if (root.packageName?.toString() != app.packageName) return@launch
            val text = PaymentNodeTextReader.read(root)
            val changedText = text != lastText
            lastText = text
            if (changedText && PaymentParser.isPaymentPage(text, app)) {
                processor.submit(PaymentObservation(app, CaptureSource.ACCESSIBILITY, session, System.currentTimeMillis(), text))
            }
            if (config.ocrEnabled && Build.VERSION.SDK_INT >= 30 &&
                (resultActivity || PaymentParser.isPaymentPage(text, app)) &&
                !Regex("支付密码|输入密码|验证码|身份证|银行卡号").containsMatchIn(text) &&
                PaymentParser.parse(PaymentObservation(app, CaptureSource.ACCESSIBILITY, session, 0, text))?.amountCents == null &&
                (screenshotSession != session || retryableOrderOcr) && !screenshotBusy) requestScreenshot(app, session)
        }
    }
    @androidx.annotation.RequiresApi(30)
    private suspend fun requestScreenshot(app: PaymentApp, capturedSession: String) {
        delay(ocrThrottle.remainingDelay(SystemClock.elapsedRealtime()))
        val config = repository.config()
        val root = rootInActiveWindow ?: return
        if (session != capturedSession || !paymentActivity || screenshotBusy || !config.enabled || !config.ocrEnabled ||
            app !in config.enabledApps || root.packageName?.toString() != app.packageName) return
        val text = PaymentNodeTextReader.read(root)
        if ((!resultActivity && !PaymentParser.isPaymentPage(text, app)) ||
            Regex("支付密码|输入密码|验证码|身份证|银行卡号").containsMatchIn(text)) return
        screenshot(app, capturedSession)
    }
    @androidx.annotation.RequiresApi(30)
    private fun screenshot(app: PaymentApp, capturedSession: String) {
        ocrThrottle.started(SystemClock.elapsedRealtime())
        screenshotBusy = true; screenshotSession = capturedSession
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()
                if (bitmap == null) { screenshotBusy = false; return }
                scope.launch {
                    try {
                        val text = ocr.recognize(bitmap, app)
                        if (session == capturedSession && rootInActiveWindow?.packageName?.toString() == app.packageName) {
                            processor.submit(PaymentObservation(app, CaptureSource.OCR, capturedSession, System.currentTimeMillis(), text))
                        }
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { control.update { it.copy(issue = RuntimeIssue.OCR_FAILED) }
                    } finally { bitmap.recycle(); screenshotBusy = false }
                }
            }
            override fun onFailure(errorCode: Int) {
                screenshotBusy = false
                control.update { it.copy(issue = RuntimeIssue.CAPTURE_BLOCKED) }
            }
        }
        if (Build.VERSION.SDK_INT >= 34) takeScreenshotOfWindow(window, mainExecutor, callback)
        else takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
    }
    override fun onInterrupt() { control.update { it.copy(issue = RuntimeIssue.DISCONNECTED) } }
    override fun onDestroy() {
        control.update { it.copy(accessibilityConnected = false, issue = RuntimeIssue.DISCONNECTED) }
        scope.cancel(); super.onDestroy()
    }
}
