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
            "交易时间,金额,商品\r\n" +
            "2026-09-01 10:00,1.00,tea\r\n"
        val preview = CsvLedgerImporter.preview(input, preset = "alipay")
        assertEquals("交易时间", preview.headers.first())
        assertEquals(1, preview.rows.size)

        val result = CsvLedgerImporter.parse(input, preview.mapping, preset = "alipay")
        assertEquals(1, result.accepted.size)
        assertEquals(4, result.accepted.single().sourceLine)
    }
}
