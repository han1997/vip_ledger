package com.han1997.vipledger.autobook

import com.han1997.vipledger.core.domain.autobook.PaymentApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentActivityClassifierTest {
    @Test fun verifiedPddOrderContainerAllowsTextButIsNotAnUnconditionalScreenshotTarget() {
        val page = "com.xunmeng.pinduoduo.activity.NewPageActivity"
        assertTrue(PaymentActivityClassifier.isPaymentActivity(PaymentApp.PDD, page))
        assertFalse(PaymentActivityClassifier.isPaymentActivity(PaymentApp.WECHAT, page))
        assertFalse(PaymentActivityClassifier.isPaymentActivity(PaymentApp.PDD, "com.xunmeng.pinduoduo.activity.OtherActivity"))
        assertFalse(PaymentActivityClassifier.isResultActivity(page))
    }
    private val wechatWeb = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI"

    @Test fun wechatWebViewBillPageIsCaptureEligibleFromRealDeviceEvidence() {
        assertTrue(PaymentActivityClassifier.isTrackedContainer(wechatWeb))
        assertTrue(PaymentActivityClassifier.isPaymentActivity(PaymentApp.WECHAT, wechatWeb))
    }

    @Test fun otherPlatformsStayFailClosedForWebViewsUntilEvidenceArrives() {
        assertFalse(PaymentActivityClassifier.isPaymentActivity(PaymentApp.JD, wechatWeb))
        assertFalse(PaymentActivityClassifier.isPaymentActivity(PaymentApp.TAOBAO, "com.example.WebViewUI"))
    }

    @Test fun chatAndUntrackedContainersStayClosed() {
        assertFalse(PaymentActivityClassifier.isPaymentActivity(PaymentApp.WECHAT, "com.tencent.mm.ui.LauncherUI"))
        assertFalse(PaymentActivityClassifier.isTrackedContainer("com.tencent.mm.ui.ToolsFragment"))
    }

    @Test fun nativePaymentAndResultContainersKeepExistingBehavior() {
        assertTrue(PaymentActivityClassifier.isPaymentActivity(PaymentApp.WECHAT, "com.tencent.mm.plugin.wallet.pay.ui.WalletPayUI"))
        assertTrue(PaymentActivityClassifier.isPaymentActivity(PaymentApp.ALIPAY, "com.example.WalletActivity"))
        assertTrue(PaymentActivityClassifier.isResultActivity("com.example.WalletPayResultUI"))
        assertFalse(PaymentActivityClassifier.isResultActivity(wechatWeb))
    }
}
