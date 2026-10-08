package com.han1997.vipledger.core.domain.autobook

import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.*

class PaymentParserTest {
    private val jdCompletedOrder = """
        完成
        测试数码旗舰店
        测试电池
        到手¥217.98
        ¥268
        退款/售后
        实付款
        共减¥50.02
        合计¥217.98
        订单编号
        jd-order-123456
        支付方式
        白条
        支付时间
        2026-09-28 09:26:03
        下单时间
        2026-09-28 09:25:58
    """.trimIndent()

    @Test fun completedJdOrderUsesActualTotalRatherThanDiscountOrProductPrice() {
        assertTrue(PaymentParser.isPaymentPage(jdCompletedOrder, PaymentApp.JD))
        val facts = assertNotNull(PaymentParser.parse(PaymentObservation(PaymentApp.JD, CaptureSource.OCR, "jd", 1, jdCompletedOrder)))
        assertEquals(21798L, facts.amountCents)
        assertEquals("测试数码旗舰店", facts.merchant)
        assertEquals("白条", facts.accountToken)
        assertEquals("jd-order-123456", facts.externalId)
        assertEquals(TransactionType.EXPENSE, facts.type)
        assertNotNull(facts.paidAt)
        assertEquals(ReviewReason.TIME, facts.reason)
    }

    @Test fun jdOrderTemplateDoesNotTreatGenericCompletionOrNotificationsAsPayment() {
        assertFalse(PaymentParser.isPaymentPage(jdCompletedOrder))
        assertFalse(PaymentParser.isPaymentPage(jdCompletedOrder, PaymentApp.ALIPAY))
        assertFalse(PaymentParser.isPaymentPage(jdCompletedOrder.replace("订单编号", "商品编号"), PaymentApp.JD))
        assertNull(parse(jdCompletedOrder, PaymentApp.JD))
        assertNull(parseBill(jdCompletedOrder.replace("合计¥217.98", "合计待计算"), PaymentApp.JD).amountCents)
        assertNull(parseBill("$jdCompletedOrder\n实付款 合计¥218.00", PaymentApp.JD).amountCents)
    }

    private val pddSignedOrder = """
        拼多多
        已签收
        返回
        测试数码旗舰店
        商品名称：测试充电器,单价: 142 元,规格描述：测试套装,数量：1个
        联系商家
        申请退款
        多件多折活动减8元,实付:,134元,(免运费)
        已购规格测试套装该规格补贴价¥142已购数量1合计补贴价¥142店铺优惠-¥8
        订单编号： 260929-123456789012345
        260929-123456789012345
        支付方式： 微信支付
        下单时间： 2026-09-29 17:21:49
        拼单时间： 2026-09-29 17:21:59
        发货时间： 2026-09-29 18:26:43
        确认收货
    """.trimIndent()

    @Test fun pddSignedOrderReadsActualAmountAndShopButAlwaysRequiresTimeReview() {
        assertTrue(PaymentParser.isPaymentPage(pddSignedOrder, PaymentApp.PDD))
        val facts = parseBill(pddSignedOrder, PaymentApp.PDD)
        assertEquals(13400L, facts.amountCents)
        assertEquals("测试数码旗舰店", facts.merchant)
        assertEquals("微信支付", facts.accountToken)
        assertEquals("260929-123456789012345", facts.externalId)
        assertEquals(TransactionType.EXPENSE, facts.type)
        assertEquals(ReviewReason.TIME, facts.reason)
        assertNull(facts.paidAt) // Order/shipping time is not a payment timestamp.
    }

    @Test fun signedOrderTemplateRequiresPddPageAndOrderEvidence() {
        assertFalse(PaymentParser.isPaymentPage(pddSignedOrder))
        assertFalse(PaymentParser.isPaymentPage(pddSignedOrder, PaymentApp.WECHAT))
        assertFalse(PaymentParser.isPaymentPage(pddSignedOrder.replace("订单编号", "商品编号"), PaymentApp.PDD))
        assertFalse(PaymentParser.isPaymentPage(pddSignedOrder.replace("已签收", "等待发货"), PaymentApp.PDD))
        assertNull(parse(pddSignedOrder, PaymentApp.PDD))
        assertNull(PaymentParser.parse(PaymentObservation(PaymentApp.WECHAT, CaptureSource.ACCESSIBILITY, "event", 1, pddSignedOrder)))
        assertNull(parseBill(pddSignedOrder.replace("实付:,134元", "实付:,0元"), PaymentApp.PDD).amountCents)
        assertNull(parseBill("$pddSignedOrder\n实付：135元", PaymentApp.PDD).amountCents)
    }

    @Test fun alipayAccessibilityDebitDescriptionAndFullRecipientLabelAreRecognized() {
        // Sanitized device node text: the visible negative amount is exposed as "支出20元".
        val text = """
            扣费解读
            测试公交服务有限公司
            支出20元
            交易成功
            支付时间
            2026-10-05 12:29:45
            2026-10-05 12:29:45
            付款方式
            测试银行信用卡(1234) 查看扣款顺序
            乘车时间
            2026-10-05 10:29:40
            收款方全称
            测试公交服务有限公司
            测试公交服务有限公司
            交易详情
            测试出行服务
            订单备注
            测试起点至测试终点
            订单号
            platform-transport-123
            platform-transport-123
            商家订单号
            merchant-transport-456
            账单管理
            返回
            账单详情
            全部账单
        """.trimIndent()
        val facts = parseBill(text, PaymentApp.ALIPAY)
        assertEquals("测试公交服务有限公司", facts.merchant)
        assertEquals(2000L, facts.amountCents)
        assertEquals(TransactionType.EXPENSE, facts.type)
        assertEquals("测试银行信用卡(1234)", facts.accountToken)
        assertEquals("platform-transport-123", facts.externalId)
        assertNotNull(facts.paidAt)
        assertEquals(ReviewReason.NONE, facts.reason)
        assertEquals("测试公交服务有限公司", parseBill(text.replace("收款方全称", "其他字段"), PaymentApp.ALIPAY).merchant)
        assertNull(parseBill(text.replace("支出20元", "收入20元"), PaymentApp.ALIPAY).type)
        assertNull(parseBill(text.replace("支出20元", "支出0元"), PaymentApp.ALIPAY).amountCents)
        assertNull(parseBill(text.replace("支出20元", "支出20.001元"), PaymentApp.ALIPAY).amountCents)
    }

    private val alipayBill = """
        账单详情
        全部账单
        测试洗衣服务有限公司
        -3.50
        交易成功
        支付时间
        2026-10-07 15:28:55
        付款方式
        测试银行信用卡(1234)
        支付奖励
        立即领取2积分
        交易详情
        测试洗衣
        订单号
        platform-order-123
        商家订单号
        merchant-order-456
    """.trimIndent()

    @Test fun alipayBillHeaderProvidesMerchantDebitAndPaymentMethod() {
        val facts = parseBill(alipayBill, PaymentApp.ALIPAY)
        assertEquals(350L, facts.amountCents)
        assertEquals(TransactionType.EXPENSE, facts.type)
        assertEquals("测试洗衣服务有限公司", facts.merchant)
        assertEquals("测试银行信用卡(1234)", facts.accountToken)
        assertEquals("platform-order-123", facts.externalId)
        assertNotNull(facts.paidAt)
        assertEquals(ReviewReason.NONE, facts.reason)
    }

    @Test fun merchantOrderLabelIsNeverMistakenForMerchantName() {
        assertEquals("", parse("支付成功\n实付金额：3.50\n商家订单号：merchant-order-456")!!.merchant)
        assertEquals("小店", parse("支付成功\n实付金额：3.50\n商家：小店")!!.merchant)
        assertEquals("", parse("支付成功\n实付金额：3.50\n付款方式：余额宝")!!.merchant)
        assertEquals("小店", parse("支付成功\n实付金额：3.50\n付款方：小店")!!.merchant)
        assertEquals("余额宝", parse("支付成功\n实付金额：3.50\n付款方式：余额宝查看扣款顺序", PaymentApp.ALIPAY)!!.accountToken)
    }

    @Test fun alipayHeaderDoesNotApplyToNotificationsOtherPlatformsOrConflictingAmounts() {
        assertNull(parse(alipayBill, PaymentApp.ALIPAY)!!.amountCents)
        assertNull(parseBill(alipayBill, PaymentApp.WECHAT).amountCents)
        assertNull(parseBill("$alipayBill\n实付金额：4.00", PaymentApp.ALIPAY).amountCents)
        assertNull(parseBill(alipayBill.replace("-3.50", "-0.00"), PaymentApp.ALIPAY).amountCents)
    }

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
    @Test fun merchantIdentityLabelsBeatProductNameRegardlessOfOrder() {
        val result = assertNotNull(parse("支付成功\n实付金额：10.00\n商品名称：纸巾\n商户名称：超市\n付款方式：余额"))
        assertEquals("超市", result.merchant)
        assertEquals("纸巾", assertNotNull(parse("支付成功\n实付金额：10.00\n商品名称：纸巾")).merchant)
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
