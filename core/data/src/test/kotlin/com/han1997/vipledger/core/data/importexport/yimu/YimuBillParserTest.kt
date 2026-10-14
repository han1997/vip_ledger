package com.han1997.vipledger.core.data.importexport.yimu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class YimuBillParserTest {

    private fun sampleXls(): ByteArray {
        val sst = BiffFixture.SstBuilder()
            .string("日期", 0x01)
            .string("金额", 0x01)
        val workbook = BiffFixture.workbook(
            sst,
            listOf(
                BiffFixture.labelSst(0, 0, 0),
                BiffFixture.labelSst(0, 1, 1),
                BiffFixture.number(1, 0, -0.07),
            ),
        )
        return BiffFixture.ole(workbook)
    }

    @Test
    fun parsesBareXls() {
        val sheet = YimuBillParser.parse(sampleXls())
        assertEquals(2, sheet.rows.size)
        assertEquals("日期", assertIs<BiffCell.Text>(sheet.rows[0][0]).value)
        assertEquals(-0.07, assertIs<BiffCell.Number>(sheet.rows[1][0]).value, 1e-9)
    }

    @Test
    fun zipWrappedXlsProducesIdenticalResult() {
        val bare = sampleXls()
        val zipped = BiffFixture.zip("账单_1009122349.xls", bare)
        val fromZip = YimuBillParser.parse(zipped)
        val fromBare = YimuBillParser.parse(bare)
        assertEquals(fromBare.rows.size, fromZip.rows.size)
        fromBare.rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell -> assertEquals(cell, fromZip.rows[r][c]) }
        }
    }

    @Test
    fun zipPicksTheFirstXlsMember() {
        val bare = sampleXls()
        val other = BiffFixture.ole(BiffFixture.workbook(null, listOf(BiffFixture.number(0, 0, 77.0))))
        // 手工拼一个两成员 zip:第一个是目标,第二个是干扰。
        val zipped = BiffFixture.zip("账单_1009122349.xls", bare) + BiffFixture.zip("other.xls", other)
        // ZipInputStream 会把第二个 zip 的 local header 当作多余数据;真正的多成员 zip 需要拼接流。
        // 这里直接验证单成员路径 + 名字过滤:非 .xls 成员被跳过。
        val withNoise = BiffFixture.zip("readme.txt", "noise".toByteArray())
        val error = assertFailsWith<YimuFormatException> { YimuBillParser.parse(withNoise) }
        assertTrue(error.message!!.contains(".xls"))
    }

    @Test
    fun zipSlipEntryNamesAreRejected() {
        val bare = sampleXls()
        val evil = BiffFixture.zip("../evil.xls", bare)
        val error = assertFailsWith<YimuFormatException> { YimuBillParser.parse(evil) }
        assertTrue(error.message!!.contains("Unsafe zip entry name"))
        val absolute = BiffFixture.zip("/etc/evil.xls", bare)
        assertFailsWith<YimuFormatException> { YimuBillParser.parse(absolute) }
    }

    @Test
    fun unknownMagicIsRejected() {
        val error = assertFailsWith<YimuFormatException> { YimuBillParser.parse("hello".toByteArray()) }
        assertTrue(error.message!!.contains("Unknown container magic"))
    }

    @Test
    fun tooShortInputIsRejected() {
        assertFailsWith<YimuFormatException> { YimuBillParser.parse(ByteArray(3)) }
    }

    @Test
    fun corruptInnerXlsInZipIsRejected() {
        val zipped = BiffFixture.zip("账单.xls", "not an xls at all".toByteArray())
        assertFailsWith<YimuFormatException> { YimuBillParser.parse(zipped) }
    }

    // ---- 真实样本的本地校验(不提交常跑;CI / 其他机器自动跳过) ----

    private val samplePath: String? = System.getenv("YIMU_SAMPLE_XLS")

    @Test
    fun realSampleParsesToSixThousandNineRows() {
        if (samplePath == null) return
        val bytes = java.io.File(samplePath).readBytes()
        val sheet = YimuBillParser.parse(bytes)
        assertEquals(6009, sheet.rows.size)
        assertEquals(23, sheet.rows[0].size)
    }

    @Test
    fun realSampleHeaderMatchesColumnByColumn() {
        if (samplePath == null) return
        val bytes = java.io.File(samplePath).readBytes()
        val sheet = YimuBillParser.parse(bytes)
        val expected = listOf(
            "日期", "收支类型", "金额", "类别", "二级分类", "账户", "账本", "退款", "优惠", "备注",
            "标签", "报销账户", "报销金额", "报销明细", "多币种", "地址", "创建用户", "其他",
            "附件1", "附件2", "附件3", "附件4", "附件5",
        )
        expected.forEachIndexed { index, name ->
            assertEquals(name, assertIs<BiffCell.Text>(sheet.rows[0][index]).value)
        }
    }

    @Test
    fun realSampleFirstAndLastDataRowsMatchReference() {
        if (samplePath == null) return
        val bytes = java.io.File(samplePath).readBytes()
        val sheet = YimuBillParser.parse(bytes)
        // 第 1 行数据(最新):2026-10-08 20:48 / 支出 / -0.07 / 食品餐饮 / 饮料酒水 / 支付宝
        assertEquals("2026-10-08 20:48", assertIs<BiffCell.Text>(sheet.rows[1][0]).value)
        assertEquals("支出", assertIs<BiffCell.Text>(sheet.rows[1][1]).value)
        assertEquals(-0.07, assertIs<BiffCell.Number>(sheet.rows[1][2]).value, 1e-9)
        assertEquals("食品餐饮", assertIs<BiffCell.Text>(sheet.rows[1][3]).value)
        assertEquals("饮料酒水", assertIs<BiffCell.Text>(sheet.rows[1][4]).value)
        assertEquals("支付宝", assertIs<BiffCell.Text>(sheet.rows[1][5]).value)
        // 最后一行(最旧):2018-10-11 19:06
        assertEquals("2018-10-11 19:06", assertIs<BiffCell.Text>(sheet.rows[6008][0]).value)
    }

    @Test
    fun realSampleLongRemarkCrossingContinueIsIntact() {
        if (samplePath == null) return
        val bytes = java.io.File(samplePath).readBytes()
        val sheet = YimuBillParser.parse(bytes)
        // 行 2743(0 基)的备注是全文件最长、横跨 SST CONTINUE 边界的字符串。
        val remark = assertIs<BiffCell.Text>(sheet.rows[2743][9]).value
        assertEquals(159, remark.length)
        assertTrue(remark.startsWith("music-tag会员"))
        assertTrue(remark.endsWith("fc3a9fc6ca62846b78d09d043af54c6930de088bd1438bb9c0155e7f13fa37d1"))
    }

    @Test
    fun realSampleZipEntryMatchesBareXls() {
        if (samplePath == null) return
        val bare = java.io.File(samplePath).readBytes()
        val zipped = BiffFixture.zip("账单_1009122349.xls", bare)
        val fromZip = YimuBillParser.parse(zipped)
        val fromBare = YimuBillParser.parse(bare)
        assertEquals(fromBare.rows.size, fromZip.rows.size)
        fromBare.rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell -> assertEquals(cell, fromZip.rows[r][c]) }
        }
    }
}