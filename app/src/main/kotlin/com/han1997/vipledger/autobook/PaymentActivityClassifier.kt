package com.han1997.vipledger.autobook

import com.han1997.vipledger.core.domain.autobook.PaymentApp

/**
 * 无障碍采集的 Activity 类名门禁：只放行支付相关容器，聊天与未取证页面保持关闭。
 * OPPO PKJ110 实测：微信账单 H5 使用 `MMWebViewUI`，拼多多订单使用 `NewPageActivity`。
 * 这些容器仅放行文本读取，仍需平台支付页面门禁才产生候选，不直接触发任意页面截图。
 */
object PaymentActivityClassifier {
    private val paymentContainer = Regex("Wallet|Pay|Trade|Order|Refund|Cashier", RegexOption.IGNORE_CASE)
    private val webViewContainer = Regex("WebView", RegexOption.IGNORE_CASE)
    private val resultContainer = Regex("(?:Pay|Trade|Refund).*(?:Success|Result)", RegexOption.IGNORE_CASE)

    fun isTrackedContainer(name: String): Boolean = name.contains("Activity") || name.endsWith("UI")

    fun isPaymentActivity(app: PaymentApp, name: String): Boolean =
        paymentContainer.containsMatchIn(name) || (app == PaymentApp.WECHAT && webViewContainer.containsMatchIn(name)) ||
            (app == PaymentApp.PDD && name == "com.xunmeng.pinduoduo.activity.NewPageActivity")

    fun isResultActivity(name: String): Boolean = resultContainer.containsMatchIn(name)

    fun isVerifiedOrderOcrActivity(app: PaymentApp, name: String): Boolean =
        app == PaymentApp.JD && name == "com.jd.lib.ordercenter.taro.OrderDetailActivityTaro"
}
