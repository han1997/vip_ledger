package com.han1997.vipledger.core.domain.autobook

import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.*

class PaymentParserTest {
    private val wechatBill = """
        测试商店
        -12.30
        原价
        ¥12.70
        优惠
        优惠¥0.40
        当前状态
        支付成功
        支付时间
        2026年10月7日 21:21:09
        支付方式
        测试储蓄卡(1234)
        交易单号
        test-order-123456
    """.trimIndent()

    private fun parseBill(text: String = wechatBill, app: PaymentApp = PaymentApp.WECHAT) =
        assertNotNull(PaymentParser.parse(PaymentObservation(app, CaptureSource.ACCESSIBILITY, "bill", 1, text)))

    @Test fun wechatBillUsesActualDebitAndPreservesHistoricalPaymentTime() {
        val result = parseBill()
        assertEquals(1230L, result.amountCents)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("test-order-123456", result.externalId)
        assertEquals("测试储蓄卡(1234)", result.accountToken)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 7, 21, 21, 9)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), result.paidAt)
        assertEquals(ReviewReason.NONE, result.reason)
    }

    @Test fun signedBillAmountRequiresWechatPageEvidenceAndUnambiguousValue() {
        assertNull(parseBill(app = PaymentApp.ALIPAY).amountCents)
        assertNull(parse(wechatBill)!!.amountCents) // Notification is not the verified bill layout.
        assertNull(parseBill(wechatBill.replace("当前状态", "状态")).amountCents)
        assertNull(parseBill(wechatBill.replace("交易单号", "其他编号")).amountCents)
        assertNull(parseBill("-8.00\n$wechatBill").amountCents)
        assertNull(parseBill("$wechatBill\n实付金额：8.00").amountCents)
        assertNull(parseBill(wechatBill.replace("-12.30", "-0.00")).amountCents)
        assertNull(parseBill(wechatBill.replace("-12.30", "-999999999999999999999.00")).amountCents)
        assertNull(parseBill("$wechatBill\nUSD").amountCents)
    }

    @Test fun chinesePaymentDateIsStrictAndMalformedDatesRequireReview() {
        assertEquals(ReviewReason.TIME, parseBill(wechatBill.replace("10月7日", "2月30日")).reason)
        assertEquals(ReviewReason.TIME, parseBill(wechatBill.replace("2026年10月7日 21:21:09", "未知")).reason)
        assertNotNull(parseBill(wechatBill.replace("2026年10月7日 21:21:09", "2026-10-07 21:21")).paidAt)
    }

    private fun parse(text: String, app: PaymentApp = PaymentApp.WECHAT) =
        PaymentParser.parse(PaymentObservation(app, CaptureSource.NOTIFICATION, "event", 1, text))

    @Test fun allPlatformsAcceptExplicitActualPaymentButIgnoreDiscount() {
        PaymentApp.entries.forEach { app ->
            val result = assertNotNull(parse("支付成功\n原价 ¥100.00\n优惠 ¥20.00\n实付金额：80.00\n付款方式：微信零钱\n商户名称：超市", app))
            assertEquals(8000, result.amountCents)
            assertEquals("微信零钱", result.accountToken)
            assertEquals(TransactionType.EXPENSE, result.type)
        }
    }
    @Test fun ambiguousAmountsAndOverflowNeverProduceAnAutomaticAmount() {
        assertNull(parse("支付成功\n¥10.00\n¥20.00")!!.amountCents)
        assertNull(parse("付款成功\n实付金额：99999999999999999999999999.00")!!.amountCents)
        assertNull(parse("付款成功\n实付金额：0.00")!!.amountCents)
    }
    @Test fun unfinishedAndDebtEventsRemainReviewable() {
        assertFalse(parse("退款申请\n退款金额：10.00")!!.success)
        assertFalse(parse("支付失败\n付款金额：10.00")!!.success)
        assertNull(parse("还款支付成功\n付款金额：10.00")!!.type)
        assertNull(parse("转账成功\n转账金额：10.00")!!.type)
        assertNull(parse("今天要买一个价值100元的东西"))
    }
    @Test fun completedRefundHasSeparateOriginalAndRefundIdentity() {
        val result = assertNotNull(parse("退款到账\n退款金额：30.00\n退款账户：储蓄卡(1234)\n原交易单号：original123\n退款单号：refund456"))
        assertTrue(result.success)
        assertEquals(TransactionType.REFUND, result.type)
        assertEquals("original123", result.originalId)
        assertEquals("refund456", result.externalId)
    }
    @Test fun ownTransferRequiresBothTokens() {
        val result = assertNotNull(parse("转账成功\n转账金额：10.00\n转出账户：微信零钱\n转入账户：银行卡（1234）"))
        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals("银行卡(1234)", result.targetToken)
    }
}
