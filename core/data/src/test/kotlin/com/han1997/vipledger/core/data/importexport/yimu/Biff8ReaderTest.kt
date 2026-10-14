package com.han1997.vipledger.core.data.importexport.yimu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Biff8ReaderTest {

    private fun sheetOf(sst: BiffFixture.SstBuilder?, records: List<ByteArray>): BiffSheet =
        Biff8Reader.readSheet(BiffFixture.workbook(sst, records))

    @Test
    fun readsTextAndNumberCells() {
        val sst = BiffFixture.SstBuilder().string("日期", 0x01).string("金额", 0x01)
        val sheet = sheetOf(
            sst,
            listOf(
                BiffFixture.labelSst(0, 0, 0),
                BiffFixture.labelSst(0, 1, 1),
                BiffFixture.number(1, 0, 0.0),
                BiffFixture.number(1, 1, -26.23),
            ),
        )
        assertEquals(2, sheet.rows.size)
        assertEquals(2, sheet.rows[0].size)
        assertEquals("日期", assertIs<BiffCell.Text>(sheet.rows[0][0]).value)
        assertEquals("金额", assertIs<BiffCell.Text>(sheet.rows[0][1]).value)
        assertEquals(-26.23, assertIs<BiffCell.Number>(sheet.rows[1][1]).value, 1e-9)
    }

    @Test
    fun compressedEightBitStringsAreDecodedAsCp1252() {
        // bit0 = 0:压缩 8-bit,每字符 1 字节。
        val sst = BiffFixture.SstBuilder().string("abc", 0x00)
        val sheet = sheetOf(sst, listOf(BiffFixture.labelSst(0, 0, 0)))
        assertEquals("abc", assertIs<BiffCell.Text>(sheet.rows[0][0]).value)
    }

    @Test
    fun richTextRunsAreSkippedWithoutBeingReadAsCharacters() {
        // bit3 = 1:先 u16 nrun,再 4*nrun 字节格式段;这些字节绝不能进字符串。
        val sst = BiffFixture.SstBuilder().string("备注", 0x01 or 0x08, richRuns = 2)
        val sheet = sheetOf(sst, listOf(BiffFixture.labelSst(0, 0, 0)))
        assertEquals("备注", assertIs<BiffCell.Text>(sheet.rows[0][0]).value)
    }

    @Test
    fun farEastExtensionBlockIsSkipped() {
        // bit2 = 1:先 u32 cbext,再 cbext 字节扩展块。
        val sst = BiffFixture.SstBuilder().string("账单", 0x01 or 0x04, farEastExtra = 5)
        val sheet = sheetOf(sst, listOf(BiffFixture.labelSst(0, 0, 0)))
        assertEquals("账单", assertIs<BiffCell.Text>(sheet.rows[0][0]).value)
    }

    @Test
    fun stringCrossingContinueBoundaryIsReassembledExactly() {
        // 容量压到 32 字节,强制字符串跨 CONTINUE;第二块偏移 0 有 options 字节。
        val long = "一木记账跨块续接验证字符串-0123456789-abcdefghij"
        val sst = BiffFixture.SstBuilder(capacity = 32).string(long, 0x01)
        val sheet = sheetOf(sst, listOf(BiffFixture.labelSst(0, 0, 0)))
        assertEquals(long, assertIs<BiffCell.Text>(sheet.rows[0][0]).value)
    }

    @Test
    fun mixedCompressionAcrossBoundaryFollowsPerChunkOptions() {
        // 第一块里放压缩字符串,跨块后第二块 options = 0(压缩),验证按新标志解。
        val first = "abcdefghij" // 压缩 8-bit
        val second = "0123456789"
        val sst = BiffFixture.SstBuilder(capacity = 24)
            .string(first, 0x00)
            .string(second, 0x00)
        val sheet = sheetOf(sst, listOf(BiffFixture.labelSst(0, 0, 0), BiffFixture.labelSst(0, 1, 1)))
        assertEquals(first, assertIs<BiffCell.Text>(sheet.rows[0][0]).value)
        assertEquals(second, assertIs<BiffCell.Text>(sheet.rows[0][1]).value)
    }

    @Test
    fun wideCharWithSingleTrailingByteDiscardsThatByte() {
        // 手工构造:第一个 payload 末尾恰好剩 1 字节(宽字符放不下),该字节被直接丢弃;
        // 第二块偏移 0 是新的 options 字节,字符从这里继续。
        val first = BiffFixture.u32(1) + BiffFixture.u32(1) + // total / unique
            BiffFixture.u16(3) + BiffFixture.u8(0x01) +        // cch=3, 全宽
            BiffFixture.u16('一'.code) + BiffFixture.u16('二'.code) +
            byteArrayOf(0x7f)                                   // 越界残留字节,必须被丢弃
        val second = byteArrayOf(0x01) + BiffFixture.u16('三'.code) // options + 第 3 个字符
        val stream = BiffFixture.bof(BiffFixture.SUBSTREAM_GLOBALS) +
            BiffFixture.record(0x00FC, first) +
            BiffFixture.record(0x003C, second) +
            BiffFixture.EOF +
            BiffFixture.bof(BiffFixture.SUBSTREAM_WORKSHEET) +
            BiffFixture.labelSst(0, 0, 0) +
            BiffFixture.EOF
        val sheet = Biff8Reader.readSheet(stream)
        assertEquals("一二三", assertIs<BiffCell.Text>(sheet.rows[0][0]).value)
    }

    @Test
    fun rkIntegerAndDoubleValuesAreDecoded() {
        val sheet = sheetOf(
            null,
            listOf(
                BiffFixture.rkInteger(0, 0, 1234),
                BiffFixture.rkInteger(0, 1, 5678, divideBy100 = true),
                BiffFixture.rkDouble(0, 2, 3.5),
                BiffFixture.mulRk(1, 0, listOf(10 to false, 20 to true)),
            ),
        )
        assertEquals(1234.0, assertIs<BiffCell.Number>(sheet.rows[0][0]).value, 1e-9)
        assertEquals(56.78, assertIs<BiffCell.Number>(sheet.rows[0][1]).value, 1e-9)
        assertEquals(3.5, assertIs<BiffCell.Number>(sheet.rows[0][2]).value, 1e-9)
        assertEquals(10.0, assertIs<BiffCell.Number>(sheet.rows[1][0]).value, 1e-9)
        assertEquals(0.2, assertIs<BiffCell.Number>(sheet.rows[1][1]).value, 1e-9)
    }

    @Test
    fun blankCellsAreExposedAsBlank() {
        val sheet = sheetOf(null, listOf(BiffFixture.blank(0, 0), BiffFixture.number(0, 1, 1.0)))
        assertEquals(BiffCell.Blank, sheet.rows[0][0])
        assertEquals(1.0, assertIs<BiffCell.Number>(sheet.rows[0][1]).value, 1e-9)
    }

    @Test
    fun missingCellsAreNullAndColumnsTakeTheMaxSeen() {
        val sheet = sheetOf(null, listOf(BiffFixture.number(0, 2, 1.0), BiffFixture.number(1, 0, 2.0)))
        assertEquals(2, sheet.rows.size)
        assertEquals(3, sheet.rows[0].size)
        assertEquals(null, sheet.rows[0][0])
        assertEquals(null, sheet.rows[0][1])
        assertEquals(2.0, assertIs<BiffCell.Number>(sheet.rows[1][0]).value, 1e-9)
    }

    @Test(timeout = 5_000)
    fun truncatedRecordThrowsInsteadOfReadingOutOfBounds() {
        val stream = BiffFixture.bof(BiffFixture.SUBSTREAM_GLOBALS) +
            BiffFixture.EOF +
            BiffFixture.bof(BiffFixture.SUBSTREAM_WORKSHEET) +
            BiffFixture.u16(0x00FD) + BiffFixture.u16(100) // 声称 100 字节但没有 payload
        assertFailsWith<YimuFormatException> { Biff8Reader.readSheet(stream) }
    }

    @Test(timeout = 5_000)
    fun sstIndexOutOfRangeThrows() {
        val sst = BiffFixture.SstBuilder().string("唯一", 0x01)
        val stream = BiffFixture.workbook(sst, listOf(BiffFixture.labelSst(0, 0, 99)))
        assertFailsWith<YimuFormatException> { Biff8Reader.readSheet(stream) }
    }

    @Test(timeout = 5_000)
    fun missingWorksheetSubstreamThrows() {
        val stream = BiffFixture.bof(BiffFixture.SUBSTREAM_GLOBALS) + BiffFixture.EOF
        assertFailsWith<YimuFormatException> { Biff8Reader.readSheet(stream) }
    }

    @Test
    fun emptySheetProducesEmptyMatrix() {
        val stream = BiffFixture.workbook(null, emptyList())
        assertTrue(Biff8Reader.readSheet(stream).rows.isEmpty())
    }

    @Test
    fun recordsAfterWorksheetEofAreIgnored() {
        // EOF 之后的记录(比如另一张 sheet)不进矩阵。
        val stream = BiffFixture.workbook(null, listOf(BiffFixture.number(0, 0, 1.0))) +
            BiffFixture.bof(BiffFixture.SUBSTREAM_WORKSHEET) +
            BiffFixture.number(5, 5, 99.0) +
            BiffFixture.EOF
        val sheet = Biff8Reader.readSheet(stream)
        assertEquals(1, sheet.rows.size)
        assertEquals(1.0, assertIs<BiffCell.Number>(sheet.rows[0][0]).value, 1e-9)
    }
}