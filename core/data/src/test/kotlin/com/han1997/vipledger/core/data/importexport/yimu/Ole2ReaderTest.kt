package com.han1997.vipledger.core.data.importexport.yimu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Ole2ReaderTest {

    @Test
    fun readsStreamFromSyntheticContainer() {
        val payload = "hello workbook".toByteArray(Charsets.UTF_8)
        val container = BiffFixture.ole(payload)
        assertContentEquals(payload, Ole2Reader.readStream(container, "Workbook"))
    }

    @Test
    fun smallStreamsGoThroughTheMiniFatPath() {
        // 100 字节 < 4096 cutoff,走 miniFAT + ministream。
        val payload = ByteArray(100) { it.toByte() }
        val container = BiffFixture.ole(payload)
        assertContentEquals(payload, Ole2Reader.readStream(container, "Workbook"))
    }

    @Test
    fun largeStreamsGoThroughTheMainFatPath() {
        // 5000 字节 > 4096 cutoff,走主 FAT。
        val payload = ByteArray(5000) { (it * 7).toByte() }
        val container = BiffFixture.ole(payload)
        assertContentEquals(payload, Ole2Reader.readStream(container, "Workbook"))
    }

    @Test(timeout = 5_000)
    fun badMagicIsRejected() {
        val container = BiffFixture.ole("data".toByteArray())
        container[0] = 0x00
        val error = assertFailsWith<YimuFormatException> { Ole2Reader.readStream(container, "Workbook") }
        assertTrue(error.message!!.contains("OLE2"))
    }

    @Test(timeout = 5_000)
    fun truncatedFileIsRejected() {
        val container = BiffFixture.ole("data".toByteArray())
        val error = assertFailsWith<YimuFormatException> {
            Ole2Reader.readStream(container.copyOf(100), "Workbook")
        }
        assertTrue(error.message!!.isNotEmpty())
    }

    @Test(timeout = 5_000)
    fun missingStreamIsRejected() {
        val container = BiffFixture.ole("data".toByteArray())
        assertFailsWith<YimuFormatException> { Ole2Reader.readStream(container, "Nope") }
    }

    @Test(timeout = 5_000)
    fun outOfBoundsSectorChainIsRejectedInsteadOfLooping() {
        val container = BiffFixture.ole("data".toByteArray())
        // 把 FAT sector 内容改成指向不存在的 sector,制造越界链。
        val fatSectorOffset = 512 + 3 * 512 // header + 3 data sectors(2 data + 1 dir)
        // 找到 FAT sector:最后一个 sector。直接把它的第一项改成巨大值。
        val lastSectorOffset = container.size - 512
        // FAT 第一项原本指向 dir sector;改成 0x7FFFFFFF 制造越界。
        putU32(container, lastSectorOffset, 0x7FFFFFFF)
        assertTrue(fatSectorOffset > 0)
        assertFailsWith<YimuFormatException> { Ole2Reader.readStream(container, "Workbook") }
    }

    @Test(timeout = 5_000)
    fun cyclicFatChainIsRejected() {
        val container = BiffFixture.ole(ByteArray(6000)) // 多 sector 数据流
        // 把 FAT sector 的第一项改成自指,制造环。
        val lastSectorOffset = container.size - 512
        putU32(container, lastSectorOffset, 0)
        assertFailsWith<YimuFormatException> { Ole2Reader.readStream(container, "Workbook") }
    }

    private fun putU32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = ((value ushr 8) and 0xff).toByte()
        target[offset + 2] = ((value ushr 16) and 0xff).toByte()
        target[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }
}
