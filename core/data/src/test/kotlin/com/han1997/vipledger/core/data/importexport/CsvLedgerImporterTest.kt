package com.han1997.vipledger.core.data.importexport

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvLedgerImporterTest {

    private val mapping = CsvMapping(mapOf(CsvField.TIME to 0, CsvField.AMOUNT to 1, CsvField.TITLE to 2))

    @Test
    fun parsesSimpleImport() {
        val input = "date,amount,title\r\n2026-09-01 10:00,1.25,tea\r\n"
        val result = CsvLedgerImporter.parse(input, CsvMapping(mapOf(CsvField.TIME to 0, CsvField.AMOUNT to 1, CsvField.TITLE to 2)))
        assertEquals("tea", result.accepted.single().title)
    }

    @Test
    fun failedRowsReportTheirSourceLineNumbers() {
        val input = "date,amount,title\r\n" +
            "2026-09-01 10:00,1.25,tea\r\n" +
            "not-a-date,2.00,coffee\r\n" +
            "2026-09-02 10:00,3.00,cake\r\n"
        val result = CsvLedgerImporter.parse(input, mapping)
        assertEquals(listOf("tea", "cake"), result.accepted.map { it.title })
        assertEquals(listOf(3), result.failedLines)
    }

    @Test
    fun zeroAndOverflowAmountsAreRejected() {
        val input = "date,amount,title\r\n2026-09-01 10:00,0,zero\r\n2026-09-01 10:00,999999999999999999999,overflow\r\n"
        val result = CsvLedgerImporter.parse(input, mapping)
        assertTrue(result.accepted.isEmpty())
        assertEquals(listOf(2, 3), result.failedLines)
    }

    @Test
    fun fingerprintMatchesTheDocumentedKeyAtMinuteGranularity() {
        val occurredAt = 1_790_208_000_000L
        val expected = MessageDigest.getInstance("SHA-1")
            .digest("${occurredAt / 60_000}|1250|acc-1|coffee".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, CsvLedgerImporter.fingerprint(occurredAt, 1_250, "acc-1", "coffee"))

        val sameMinute = occurredAt + 30_000
        assertEquals(
            CsvLedgerImporter.fingerprint(occurredAt, 1_250, "acc-1", "coffee"),
            CsvLedgerImporter.fingerprint(sameMinute, 1_250, "acc-1", "coffee"),
        )
        assertTrue(
            CsvLedgerImporter.fingerprint(occurredAt, 1_250, "acc-1", "coffee") !=
                CsvLedgerImporter.fingerprint(occurredAt + 60_000, 1_250, "acc-1", "coffee"),
        )
    }

    @Test
    fun previewAttributesInvalidFieldsToTheFieldThatFailed() {
        val input = "交易时间,金额,商品\r\n2026-09-01 10:00,abc,咖啡\r\n"
        val preview = CsvLedgerImporter.preview(input, preset = "wechat")
        assertEquals(setOf(CsvField.AMOUNT), preview.invalidFields)
    }

    @Test
    fun previewReportsMissingRequiredColumns() {
        val input = "交易时间,金额\r\n2026-09-01 10:00,1.00\r\n"
        val preview = CsvLedgerImporter.preview(input, preset = "wechat")
        assertEquals(setOf(CsvField.TITLE), preview.invalidFields)
    }

    @Test
    fun previewAndParseAgreeOnTheHeaderRowInsideABillPreamble() {
        val input = "支付宝交易记录明细查询\r\n" +
            "账号:someone@example.com\r\n" +
            "起始时间:[2026-09-01 00:00:00] 终止时间:[2026-09-30 23:59:59]\r\n" +
            "交易时间,金额,商品\r\n" +
            "2026-09-01 10:00,1.00,tea\r\n"
        val preview = CsvLedgerImporter.preview(input, preset = "alipay")
        assertEquals("交易时间", preview.headers.first())
        assertEquals(1, preview.rows.size)

        val result = CsvLedgerImporter.parse(input, preview.mapping, preset = "alipay")
        assertEquals(1, result.accepted.size)
        assertEquals(5, result.accepted.single().sourceLine)
    }

    @Test
    fun detectsRealWeChatHeaderAfterMetadataPreamble() {
        val preview = CsvLedgerImporter.preview(WECHAT_BILL, preset = "wechat")

        // 「起始时间/终止时间」前导行只命中 1 个字段，真表头命中 6 个：必须选中真表头。
        assertEquals(WECHAT_HEADER, preview.headers)
        assertTrue(preview.invalidFields.isEmpty(), "真实微信账单不应缺列或字段非法")

        val result = CsvLedgerImporter.parse(WECHAT_BILL, preview.mapping, preset = "wechat")
        assertEquals(5, result.accepted.size)
        assertEquals(listOf(12), result.failedLines, "转账行应作为失败行报告，不阻断整批")
        // 预览与解析认的是同一表头行：表头在第 8 行，首条数据行号即 9。
        assertEquals(9, result.accepted.first().sourceLine)
        assertEquals("早餐店", result.accepted.first().title)
    }

    @Test
    fun detectsRealAlipayHeaderAfterMetadataPreamble() {
        val preview = CsvLedgerImporter.preview(ALIPAY_BILL, preset = "alipay")

        assertEquals(ALIPAY_HEADER, preview.headers)
        assertTrue(preview.invalidFields.isEmpty(), "真实支付宝账单不应缺列或字段非法")

        val result = CsvLedgerImporter.parse(ALIPAY_BILL, preview.mapping, preset = "alipay")
        assertEquals(3, result.accepted.size)
        assertTrue(result.failedLines.isEmpty())
        assertEquals(
            preview.rows.map { it[2] },
            result.accepted.map { it.title },
            "预览与解析必须认同一表头，逐行标题应一一对应",
        )
    }

    private companion object {
        val WECHAT_HEADER = listOf(
            "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
            "支付方式", "当前状态", "交易单号", "商户单号", "备注",
        )

        val WECHAT_BILL = "微信支付账单明细\r\n" +
            "微信昵称：[测试用户]\r\n" +
            "起始时间：[2026-09-01 00:00:00] 终止时间：[2026-09-30 23:59:59]\r\n" +
            "导出类型：[全部]\r\n" +
            "导出时间：[2026-10-01 10:00:00]\r\n" +
            "\r\n" +
            "微信支付账单明细列表\r\n" +
            "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注\r\n" +
            "2026-09-01 08:15:00,商户消费,早餐店,豆浆油条,支出,12.50,零钱,支付成功,1001,2001,\r\n" +
            "2026-09-02 12:30:00,商户消费,公司食堂,午餐,支出,23.80,零钱,支付成功,1002,2002,\r\n" +
            "2026-09-03 19:05:00,商户消费,超市,日用百货,支出,66.00,零钱通,支付成功,1003,2003,周末采购\r\n" +
            "2026-09-05 09:00:00,转账,同事小李,转账,支出,100.00,零钱,支付成功,1004,2004,\r\n" +
            "2026-09-08 21:45:00,商户消费,奶茶店,珍珠奶茶,支出,18.00,零钱,支付成功,1005,2005,\r\n" +
            "2026-09-10 07:20:00,商户消费,便利店,面包牛奶,支出,9.90,零钱,支付成功,1006,2006,\r\n"

        val ALIPAY_HEADER = listOf(
            "交易时间", "交易分类", "交易对方", "商品说明", "收/支", "金额",
            "收/付款方式", "交易状态", "交易订单号", "商家订单号", "备注",
        )

        val ALIPAY_BILL = "支付宝交易记录明细查询\r\n" +
            "账号:[someone@example.com]\r\n" +
            "起始时间:[2026-09-01 00:00:00] 终止时间:[2026-09-30 23:59:59]\r\n" +
            "导出时间:[2026-10-01 10:00:00]\r\n" +
            "\r\n" +
            "交易记录明细列表\r\n" +
            "交易时间,交易分类,交易对方,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注\r\n" +
            "2026-09-01 09:10:00,餐饮美食,早餐店,豆浆油条,支出,12.50,余额宝,交易成功,2026090100001,3001,\r\n" +
            "2026-09-02 12:30:00,餐饮美食,公司食堂,午餐,支出,23.80,余额,交易成功,2026090200001,3002,\r\n" +
            "2026-09-03 19:05:00,日用百货,超市,日用百货,支出,66.00,花呗,交易成功,2026090300001,3003,周末采购\r\n"
    }
}
