package com.han1997.vipledger.core.data.importexport.yimu

import java.io.ByteArrayOutputStream

/**
 * 测试专用:最小 BIFF8 / OLE2 / zip 字节构造器。
 *
 * 只在 `src/test` 里使用,不进 main source set、不随 APK 发布。用于在无真实样本的机器上
 * 也能回归 SST 跨 CONTINUE 续接、四种字符串头、RK 解码、坏路径异常等核心逻辑。
 */
internal object BiffFixture {

    const val SUBSTREAM_GLOBALS = 0x0005
    const val SUBSTREAM_WORKSHEET = 0x0010

    fun record(id: Int, payload: ByteArray): ByteArray =
        u16(id) + u16(payload.size) + payload

    fun bof(type: Int): ByteArray = record(
        0x0809,
        u16(0x0600) + u16(type) + u16(0) + u16(0) + u32(0) + u32(0),
    )

    val EOF: ByteArray = record(0x000A, ByteArray(0))

    fun labelSst(row: Int, col: Int, sstIndex: Int): ByteArray =
        record(0x00FD, u16(row) + u16(col) + u16(0) + u32(sstIndex))

    fun number(row: Int, col: Int, value: Double): ByteArray =
        record(0x0203, u16(row) + u16(col) + u16(0) + double(value))

    fun rkInteger(row: Int, col: Int, value: Int, divideBy100: Boolean = false): ByteArray {
        val bits = (value shl 2) or 0x02 or (if (divideBy100) 0x01 else 0x00)
        return record(0x027E, u16(row) + u16(col) + u16(0) + u32(bits))
    }

    fun rkDouble(row: Int, col: Int, value: Double, divideBy100: Boolean = false): ByteArray {
        val high = (value.toRawBits() ushr 32) and 0xFFFFFFFCL
        val bits = high.toInt() or (if (divideBy100) 0x01 else 0x00)
        return record(0x027E, u16(row) + u16(col) + u16(0) + u32(bits))
    }

    fun mulRk(row: Int, firstCol: Int, values: List<Pair<Int, Boolean>>): ByteArray {
        // 布局:rw(2) + colFirst(2) + n*(ixfe(2) + RK(4)) + colLast(2)。
        var payload = u16(row) + u16(firstCol)
        values.forEach { (value, divide) ->
            val bits = (value shl 2) or 0x02 or (if (divide) 0x01 else 0x00)
            payload += u16(0) + u32(bits)
        }
        payload += u16(firstCol + values.size - 1)
        return record(0x00BD, payload)
    }

    fun blank(row: Int, col: Int): ByteArray = record(0x0201, u16(row) + u16(col) + u16(0))

    fun rowRecord(row: Int): ByteArray =
        record(0x0208, u16(row) + u16(0) + u16(1) + u16(0) + u16(0) + u16(0))

    /**
     * 构造一个完整 `Workbook` 流:globals 子流(BOF + 可选 BOUNDSHEET + SST) + worksheet 子流
     * (BOF + 单元格记录 + EOF) + globals EOF。
     */
    fun workbook(
        sst: SstBuilder?,
        sheetRecords: List<ByteArray>,
        boundsheet: Boolean = true,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(bof(SUBSTREAM_GLOBALS))
        if (boundsheet) out.write(record(0x0085, u32(0) + u16(0) + u8(0) + u8(1) + u16('账'.code) + u16('单'.code)))
        sst?.build()?.forEachIndexed { index, chunk ->
            out.write(record(if (index == 0) 0x00FC else 0x003C, chunk))
        }
        out.write(EOF)
        out.write(bof(SUBSTREAM_WORKSHEET))
        sheetRecords.forEach(out::write)
        out.write(EOF)
        return out.toByteArray()
    }

    /**
     * SST 构造器。把 `cch/grbit/字符` 写进最多 [capacity] 字节一块的 payload,
     * 跨块时模拟 Excel 的续接规则:在下一块偏移 0 写一个 options 字节;
     * 宽字符遇到旧块末尾只剩 1 字节时,那 1 字节留空(解码侧会丢弃)。
     */
    class SstBuilder(private val capacity: Int = 8224) {
        private val chunks = ArrayList<ByteArrayOutputStream>().apply { add(ByteArrayOutputStream(capacity)) }
        private var stringCount = 0

        private fun current(): ByteArrayOutputStream = chunks.last()
        private fun remaining(): Int = capacity - current().size()

        private fun newChunk(options: Int?) {
            val chunk = ByteArrayOutputStream(capacity)
            if (options != null) chunk.write(options)
            chunks.add(chunk)
        }

        private fun raw(bytes: ByteArray) {
            var index = 0
            while (index < bytes.size) {
                if (remaining() <= 0) newChunk(null)
                val take = minOf(bytes.size - index, remaining())
                current().write(bytes, index, take)
                index += take
            }
        }

        private fun writeChar(code: Int) {
            current().write(code and 0xff)
            current().write((code ushr 8) and 0xff)
        }

        /**
         * 追加一个字符串。[grbit] 的 bit0 = fHighByte、bit2 = Far-East、bit3 = rich-text。
         * [richRuns] / [farEastExtra] 给出要跳过的额外字节,调用方负责构造真实布局。
         */
        fun string(text: String, grbit: Int, richRuns: Int = 0, farEastExtra: Int = 0): SstBuilder {
            val wide = grbit and 0x01 != 0
            raw(u16(text.length))
            raw(byteArrayOf(grbit.toByte()))
            if (grbit and 0x08 != 0) raw(u16(richRuns))
            if (grbit and 0x04 != 0) raw(u32(farEastExtra))
            text.forEach { ch ->
                val perChar = if (wide) 2 else 1
                if (remaining() < perChar) newChunk(if (wide) 1 else 0)
                if (wide) writeChar(ch.code) else current().write(ch.code and 0xff)
            }
            if (richRuns > 0) raw(ByteArray(richRuns * 4))
            if (farEastExtra > 0) raw(ByteArray(farEastExtra))
            stringCount++
            return this
        }

        fun build(): List<ByteArray> {
            val header = u32(stringCount) + u32(stringCount)
            val first = chunks.first().toByteArray()
            // 8 字节 SST 头顶在第一个 payload 的最前面(与真实文件一致:头紧接 cch 起)。
            val firstBytes = header + first
            val result = ArrayList<ByteArray>(chunks.size)
            result.add(firstBytes)
            for (i in 1 until chunks.size) result.add(chunks[i].toByteArray())
            return result
        }
    }

    /** 构造一个 OLE2/CFB 容器,内含单个命名流(默认 "Workbook")。小流自动走 miniFAT。 */
    fun ole(stream: ByteArray, streamName: String = "Workbook"): ByteArray {
        val sectorSize = 512
        val miniSectorSize = 64
        val miniCutoff = 4096
        val useMini = stream.size < miniCutoff

        val sectors = ArrayList<ByteArray>()
        var streamStart = 0
        var rootSize = 0
        var firstMiniFat = 0xFFFFFFFE.toInt()
        var numMiniFat = 0

        if (useMini) {
            val miniCount = maxOf(1, (stream.size + miniSectorSize - 1) / miniSectorSize)
            val miniStreamBytes = ByteArray(miniCount * miniSectorSize)
            System.arraycopy(stream, 0, miniStreamBytes, 0, stream.size)
            // ministream 本身按主 sector 存放,至少占 1 个 sector(向上取整)。
            val miniStreamSectors = maxOf(1, (miniStreamBytes.size + sectorSize - 1) / sectorSize)
            val paddedStream = ByteArray(miniStreamSectors * sectorSize)
            System.arraycopy(miniStreamBytes, 0, paddedStream, 0, miniStreamBytes.size)
            streamStart = sectors.size
            repeat(miniStreamSectors) { sectors.add(slice(paddedStream, it * sectorSize, sectorSize)) }
            rootSize = miniStreamBytes.size

            val miniFatEntries = miniCount
            val miniFatSectors = maxOf(1, (miniFatEntries * 4 + sectorSize - 1) / sectorSize)
            val miniFatBytes = ByteArray(miniFatSectors * sectorSize)
            for (i in 0 until miniCount) {
                val next = if (i == miniCount - 1) 0xFFFFFFFE.toInt() else i + 1
                putU32(miniFatBytes, i * 4, next)
            }
            for (i in miniCount until (miniFatBytes.size / 4)) putU32(miniFatBytes, i * 4, 0xFFFFFFFF.toInt())
            firstMiniFat = sectors.size
            numMiniFat = miniFatSectors
            repeat(miniFatSectors) { sectors.add(slice(miniFatBytes, it * sectorSize, sectorSize)) }
        } else {
            val dataSectors = (stream.size + sectorSize - 1) / sectorSize
            streamStart = sectors.size
            repeat(dataSectors) { sectors.add(slice(stream, it * sectorSize, sectorSize)) }
        }

        val dirSector = sectors.size
        val dirBytes = ByteArray(sectorSize)
        writeDirEntry(dirBytes, 0, "Root Entry", 5, if (useMini) streamStart else 0xFFFFFFFE.toInt(), rootSize.toLong())
        writeDirEntry(dirBytes, 1, streamName, 2, if (useMini) 0 else streamStart, stream.size.toLong())
        sectors.add(dirBytes)

        val fatSector = sectors.size
        val fat = IntArray(sectorSize / 4) { 0xFFFFFFFF.toInt() }
        if (useMini) {
            val miniStreamSectors = maxOf(1, (rootSize + sectorSize - 1) / sectorSize)
            for (i in 0 until miniStreamSectors) {
                val next = if (i == miniStreamSectors - 1) -2 else i + 1
                fat[streamStart + i] = next
            }
            for (i in 0 until numMiniFat) {
                val next = if (i == numMiniFat - 1) -2 else streamStart + miniStreamSectors + i + 1
                fat[firstMiniFat + i] = next
            }
        } else {
            val dataSectors = (stream.size + sectorSize - 1) / sectorSize
            for (i in 0 until dataSectors) {
                val next = if (i == dataSectors - 1) -2 else i + 1
                fat[streamStart + i] = next
            }
        }
        fat[dirSector] = -2
        fat[fatSector] = -3
        sectors.add(intArrayToBytes(fat))

        val header = ByteArray(sectorSize)
        val magic = byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11.toByte(), 0xe0.toByte(),
            0xa1.toByte(), 0xb1.toByte(), 0x1a.toByte(), 0xe1.toByte())
        System.arraycopy(magic, 0, header, 0, magic.size)
        putU16(header, 24, 0x003E)
        putU16(header, 26, 0x0003)
        putU16(header, 28, 0xFFFE)
        putU16(header, 30, 9)
        putU16(header, 32, 6)
        putU32(header, 40, 0)
        putU32(header, 44, 1)
        putU32(header, 48, dirSector)
        putU32(header, 56, miniCutoff)
        putU32(header, 60, firstMiniFat)
        putU32(header, 64, numMiniFat)
        putU32(header, 68, 0xFFFFFFFE.toInt())
        putU32(header, 72, 0)
        for (i in 0 until 109) putU32(header, 76 + i * 4, if (i == 0) fatSector else 0xFFFFFFFF.toInt())

        val out = ByteArrayOutputStream()
        out.write(header)
        sectors.forEach(out::write)
        return out.toByteArray()
    }

    /** 构造一个只含单个条目的 zip。 */
    fun zip(entryName: String, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val nameBytes = entryName.toByteArray(Charsets.UTF_8)
        val crc = java.util.zip.CRC32().apply { update(content) }.value.toInt()
        out.write(u32(0x04034b50))
        out.write(u16(20))
        out.write(u16(0x0800)) // UTF-8 名字标志
        out.write(u16(0))
        out.write(u16(0))
        out.write(u16(0))
        out.write(u32(crc))
        out.write(u32(content.size))
        out.write(u32(content.size))
        out.write(u16(nameBytes.size))
        out.write(u16(0))
        out.write(nameBytes)
        out.write(content)

        val centralOffset = out.size()
        out.write(u32(0x02014b50))
        out.write(u16(20))
        out.write(u16(20))
        out.write(u16(0x0800))
        out.write(u16(0))
        out.write(u16(0))
        out.write(u16(0))
        out.write(u32(crc))
        out.write(u32(content.size))
        out.write(u32(content.size))
        out.write(u16(nameBytes.size))
        out.write(u16(0))
        out.write(u16(0))
        out.write(u16(0))
        out.write(u16(0))
        out.write(u32(0))
        out.write(u32(0))
        out.write(nameBytes)

        val centralSize = out.size() - centralOffset
        out.write(u32(0x06054b50))
        out.write(u16(0))
        out.write(u16(0))
        out.write(u16(1))
        out.write(u16(1))
        out.write(u32(centralSize))
        out.write(u32(centralOffset))
        out.write(u16(0))
        return out.toByteArray()
    }

    // ---- 小端写入 ----

    fun u8(value: Int): ByteArray = byteArrayOf(value.toByte())

    fun u16(value: Int): ByteArray = byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())

    fun u32(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte(),
        ((value ushr 16) and 0xff).toByte(), ((value ushr 24) and 0xff).toByte(),
    )

    fun double(value: Double): ByteArray {
        val bits = value.toRawBits()
        val out = ByteArray(8)
        for (i in 0 until 8) out[i] = ((bits ushr (i * 8)) and 0xff).toByte()
        return out
    }

    private fun slice(source: ByteArray, offset: Int, length: Int): ByteArray {
        val out = ByteArray(length)
        val available = minOf(length, source.size - offset)
        if (available > 0) System.arraycopy(source, offset, out, 0, available)
        return out
    }

    private fun intArrayToBytes(values: IntArray): ByteArray {
        val out = ByteArray(values.size * 4)
        values.indices.forEach { putU32(out, it * 4, values[it]) }
        return out
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun putU32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = ((value ushr 8) and 0xff).toByte()
        target[offset + 2] = ((value ushr 16) and 0xff).toByte()
        target[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }

    private fun writeDirEntry(
        dir: ByteArray,
        index: Int,
        name: String,
        type: Int,
        startSector: Int,
        size: Long,
    ) {
        val base = index * 128
        val nameBytes = name.toByteArray(Charsets.UTF_16LE)
        System.arraycopy(nameBytes, 0, dir, base, nameBytes.size)
        dir[base + nameBytes.size] = 0
        dir[base + nameBytes.size + 1] = 0
        putU16(dir, base + 64, nameBytes.size + 2)
        dir[base + 66] = type.toByte()
        putU32(dir, base + 116, startSector)
        putU32(dir, base + 120, (size and 0xFFFFFFFFL).toInt())
        putU32(dir, base + 124, 0)
    }
}