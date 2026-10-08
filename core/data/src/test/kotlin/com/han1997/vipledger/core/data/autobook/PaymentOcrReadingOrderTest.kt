package com.han1997.vipledger.core.data.autobook

import com.han1997.vipledger.core.domain.autobook.*
import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.*

class PaymentOcrReadingOrderTest {
    @Test fun separatedLabelAndValueBlocksBecomeRowsAndParseAsJdOrder() {
        // Real ML Kit geometry and observed label errors; personal/order text replaced.
        val lines = listOf(
            PaymentOcrLine("测试数码旗舰店", 66, 389, 588, 450),
            PaymentOcrLine("图片内品牌旗舰店", 132, 523, 317, 546),
            PaymentOcrLine("实付款", 68, 1077, 229, 1128),
            PaymentOcrLine("订单编号", 67, 1184, 280, 1245),
            PaymentOcrLine("支付方式", 67, 1447, 281, 1496),
            PaymentOcrLine("支付时闻", 67, 1700, 282, 1756),
            PaymentOcrLine("下单时间", 66, 1827, 283, 1882),
            PaymentOcrLine("o完成", 586, 194, 848, 280),
            PaymentOcrLine("进店>", 1208, 398, 1351, 444),
            PaymentOcrLine("共减¥50.02 台计¥217.98>", 624, 1072, 1367, 1133),
            PaymentOcrLine("12345678901234561复制", 674, 1199, 1393, 1254),
            PaymentOcrLine("2026-09-28 09:26:03", 798, 1717, 1375, 1759),
            PaymentOcrLine("白条>", 1223, 1454, 1365, 1508),
            PaymentOcrLine("2026-09-28 09:25:58", 800, 1845, 1375, 1888),
        )
        val text = PaymentOcrReadingOrder.format(lines)
        assertTrue(text.contains("实付款 共减¥50.02 台计¥217.98>"))
        assertTrue(text.contains("支付时闻 2026-09-28 09:26:03"))
        val facts = assertNotNull(PaymentParser.parse(PaymentObservation(PaymentApp.JD, CaptureSource.OCR, "jd", 1, text)))
        assertEquals(21798L, facts.amountCents)
        assertEquals("测试数码旗舰店", facts.merchant)
        assertEquals("白条", facts.accountToken)
        assertEquals(TransactionType.EXPENSE, facts.type)
        assertEquals(ReviewReason.TIME, facts.reason)
        assertNotNull(facts.paidAt)
        assertEquals("12345678901234561", facts.externalId) // Never guess-correct OCR digits.
        assertNull(PaymentParser.parse(PaymentObservation(PaymentApp.JD, CaptureSource.NOTIFICATION, "jd", 1, text)))
    }

    @Test fun adjacentRowsStaySeparateAndInputBlockOrderDoesNotMatter() {
        val lines = listOf(
            PaymentOcrLine("订单号", 0, 100, 60, 120), PaymentOcrLine("abc123", 100, 102, 150, 122),
            PaymentOcrLine("支付方式", 0, 140, 70, 160), PaymentOcrLine("白条", 100, 141, 150, 161),
        )
        assertEquals("订单号 abc123\n支付方式 白条", PaymentOcrReadingOrder.format(lines.reversed()))
        assertEquals("", PaymentOcrReadingOrder.format(emptyList()))
    }
}
