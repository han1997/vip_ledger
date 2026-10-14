package com.han1997.vipledger.core.data.importexport.yimu

import java.io.ByteArrayOutputStream

/**
 * OLE2 / Compound File Binary (CFB) 容器解包器:从 .xls 字节里取出命名流(如 "Workbook")。
 *
 * 零第三方依赖,纯 Kotlin。支持主 FAT 与 miniFAT(ministream)两条取流路径。
 * 任何坏魔数 / 长度不足 / 越界 sector 链 / 链成环 / 保留值违规都抛 [YimuFormatException] 而不是 OOM。
 */
object Ole2Reader {

    /** OLE2/CFB 头魔数 d0 cf 11 e0 a1 b1 1a e1。 */
    private val MAGIC = byteArrayOf(
        0xd0.toByte(), 0xcf.toByte(), 0x11.toByte(), 0xe0.toByte(),
        0xa1.toByte(), 0xb1.toByte(), 0x1a.toByte(), 0xe1.toByte(),
    )

    private const val SIGNATURE_SIZE = 512

    /** 目录项长度。 */
    private const val DIR_ENTRY_SIZE = 128

    /** 头内 DIFAT 项数固定 109,起始偏移 76。 */
    private const val DIFAT_IN_HEADER = 109

    /** 目录项类型:5 = root storage,2 = stream。 */
    private const val ROOT_TYPE = 5
    private const val STREAM_TYPE = 2

    /** 名字 UTF-16LE 字段最大字节数。 */
    private const val NAME_MAX_BYTES = 64

    /**
     * FAT 保留 / 特殊入口值(用 Long 承载避免 Int 符号位干扰)。凡 `< SPECIAL_MIN` 的才是有效 sector 序号:
     * - 0xFFFFFFFE = ENDOFCHAIN(链终点);
     * - 0xFFFFFFFD = FATSECT;0xFFFFFFFC = DIFSECT;0xFFFFFFFF = FREESECT / NOSTREAM。
     * 遍历链时撞到除 ENDOFCHAIN 之外的保留值一律抛异常。
     */
    private const val SPECIAL_MIN = 0xFFFFFFFAL
    private const val ENDOFCHAIN = 0xFFFFFFFEL

    /** 防链成环 / 恶意超长链的硬上限:合法文件 sector 数远小于此。 */
    private const val MAX_SECTORS = 4_000_000

    private const val ROOT_ENTRY_NAME = "Root Entry"
    private const val WORKBOOK_STREAM_FALLBACK = "Workbook"

    /**
     * 从 OLE2/CFB 容器中取出名为 [streamName] 的流(如 "Workbook")。
     *
     * @throws YimuFormatException 魔数不符 / 结构截断 / 越界 sector / 链成环 / 保留值违规。
     */
    fun readStream(bytes: ByteArray, streamName: String): ByteArray {
        if (bytes.size < SIGNATURE_SIZE) throw YimuFormatException("OLE2 file too short (${bytes.size} bytes)")
        if (!hasMagic(bytes)) throw YimuFormatException("Not an OLE2/CFB file")

        val sectorShift = littleU16(bytes, 30)
        val miniSectorShift = littleU16(bytes, 32)
        if (sectorShift < 7 || sectorShift > 12) throw YimuFormatException("Unsupported sector shift $sectorShift")
        if (miniSectorShift < 2 || miniSectorShift > 12) throw YimuFormatException("Unsupported mini sector shift $miniSectorShift")
        val sectorSize = 1 shl sectorShift
        val miniSectorSize = 1 shl miniSectorShift

        val numFatSectors = littleU32(bytes, 44)
        val firstDirSector = littleU32(bytes, 48)
        val miniCutoff = littleU32(bytes, 56)
        val firstMiniFatSector = littleU32(bytes, 60)
        val numMiniFatSectors = littleU32(bytes, 64)
        val firstDifatSector = littleU32(bytes, 68)
        val numDifatSectors = littleU32(bytes, 72)

        if (numFatSectors > MAX_SECTORS) throw YimuFormatException("FAT too large ($numFatSectors)")

        val fat = readFat(bytes, sectorSize, numFatSectors.toInt(), firstDifatSector, numDifatSectors.toInt())

        val dirBytes = readChain(bytes, sectorSize, firstDirSector, fat, null)
        if (dirBytes.isEmpty()) throw YimuFormatException("Empty directory")

        val entry = findEntry(dirBytes, streamName)
            ?: throw YimuFormatException("Stream '$streamName' not found")
        val streamSize = entry.size
        if (streamSize < 0) throw YimuFormatException("Invalid stream size $streamSize")

        return if (streamSize < miniCutoff) {
            // miniFAT + ministream 路径:小流存放在 root entry 的 ministream 里。
            val root = findEntry(dirBytes, ROOT_ENTRY_NAME) ?: throw YimuFormatException("Root entry missing")
            val miniFatBytes = readChain(
                bytes, sectorSize, firstMiniFatSector, fat, numMiniFatSectors.toLong() * sectorSize,
            )
            // miniFAT 是紧凑的 u32 数组(每项一个 mini sector 序号),不是按 miniSectorSize 分块。
            val miniFat = toSectorTable(miniFatBytes)
            val miniStream = readChain(bytes, sectorSize, root.startSector.toLong(), fat, root.size)
            // ministream 是拼接好的纯字节数组:mini sector N 从 N*miniSectorSize 开始,没有头偏移。
            readChain(miniStream, miniSectorSize, entry.startSector.toLong(), miniFat, streamSize, headerSize = 0)
        } else {
            readChain(bytes, sectorSize, entry.startSector.toLong(), fat, streamSize)
        }
    }

    private data class DirEntry(val name: String, val type: Int, val startSector: Int, val size: Long)

    /** 读 FAT:头内 109 项 + DIFAT 链指向的额外 FAT sector 序号。 */
    private fun readFat(
        bytes: ByteArray,
        sectorSize: Int,
        numFatSectors: Int,
        firstDifatSector: Long,
        numDifatSectors: Int,
    ): IntArray {
        if (numFatSectors < 0) throw YimuFormatException("Negative FAT size")
        val fatSectorIds = ArrayList<Int>(numFatSectors)
        for (i in 0 until DIFAT_IN_HEADER) {
            if (fatSectorIds.size >= numFatSectors) break
            val s = littleU32(bytes, 76 + i * 4)
            if (s < SPECIAL_MIN) fatSectorIds.add(s.toInt())
        }
        var difatSector = firstDifatSector
        var visited = 0
        val entriesPerSector = sectorSize / 4 - 1
        while (difatSector < SPECIAL_MIN && fatSectorIds.size < numFatSectors) {
            if (++visited > numDifatSectors + 1 || visited > MAX_SECTORS) {
                throw YimuFormatException("DIFAT chain too long")
            }
            val base = sectorOffset(bytes, sectorSize, difatSector.toInt(), SIGNATURE_SIZE)
            for (i in 0 until entriesPerSector) {
                if (fatSectorIds.size >= numFatSectors) break
                val s = littleU32(bytes, base + i * 4)
                if (s < SPECIAL_MIN) fatSectorIds.add(s.toInt())
            }
            difatSector = littleU32(bytes, base + entriesPerSector * 4)
        }
        if (fatSectorIds.size < numFatSectors) throw YimuFormatException("FAT sectors missing")

        val words = sectorSize / 4
        val fat = IntArray(numFatSectors * words)
        for ((index, sector) in fatSectorIds.withIndex()) {
            val base = sectorOffset(bytes, sectorSize, sector, SIGNATURE_SIZE)
            for (i in 0 until words) {
                fat[index * words + i] = littleU32(bytes, base + i * 4).toInt()
            }
        }
        return fat
    }

    /**
     * 按 [fat] 把 [startSector] 起的链拼成字节;给了 [expectedSize] 就只取前那多字节。
     *
     * [headerSize] 是 sector 0 之前的固定偏移:主容器里是 512 字节 CFB 头,
     * ministream 是拼接好的纯字节数组,没有头,必须传 0。
     */
    private fun readChain(
        bytes: ByteArray,
        sectorSize: Int,
        startSectorLong: Long,
        fat: IntArray,
        expectedSize: Long?,
        headerSize: Int = SIGNATURE_SIZE,
    ): ByteArray {
        if (startSectorLong >= SPECIAL_MIN) {
            if (startSectorLong == ENDOFCHAIN && expectedSize == 0L) return ByteArray(0)
            throw YimuFormatException("Invalid chain start 0x%08x".format(startSectorLong))
        }
        val out = ByteArrayOutputStream()
        var sector = startSectorLong.toInt()
        var count = 0
        while (true) {
            // 合法链不会回头:链长超过 FAT 项数必有环。用 fat.size 兜底,避免循环写爆内存。
            if (++count > fat.size) throw YimuFormatException("Sector chain too long or cyclic")
            val base = sectorOffset(bytes, sectorSize, sector, headerSize)
            out.write(bytes, base, sectorSize)
            val next = fat[sector].toLong() and 0xFFFFFFFFL
            if (next == ENDOFCHAIN) break
            if (next >= SPECIAL_MIN) {
                throw YimuFormatException("Unexpected FAT marker 0x%08x in chain".format(next))
            }
            sector = next.toInt()
        }
        val all = out.toByteArray()
        if (expectedSize == null) return all
        if (expectedSize > all.size) {
            throw YimuFormatException("Sector chain shorter than declared size ($expectedSize > ${all.size})")
        }
        return if (expectedSize == 0L) ByteArray(0) else all.copyOf(expectedSize.toInt())
    }

    /** 读 miniFAT:紧凑 u32 数组,每项一个 mini sector 序号。 */
    private fun toSectorTable(bytes: ByteArray): IntArray {
        val n = bytes.size / 4
        val out = IntArray(n)
        for (i in 0 until n) out[i] = littleU32(bytes, i * 4).toInt()
        return out
    }

    /** 从拼接好的目录字节里找名字匹配 [name] 的流条目。 */
    private fun findEntry(dirBytes: ByteArray, name: String): DirEntry? {
        val count = dirBytes.size / DIR_ENTRY_SIZE
        for (i in 0 until count) {
            val base = i * DIR_ENTRY_SIZE
            val nameLen = littleU16(dirBytes, base + 64)
            if (nameLen < 2 || nameLen > NAME_MAX_BYTES) continue
            val actual = nameLen - 2
            val entryName = decodeUtf16(dirBytes, base, actual)
            val type = dirBytes[base + 66].toInt() and 0xff
            if (type != STREAM_TYPE && type != ROOT_TYPE) continue
            if (entryName == name) {
                val startSector = littleU32(dirBytes, base + 116)
                // 大小取低 4 字节(BIFF8 只用低 32 位,高 4 字节可能非 0)。
                val size = littleU32(dirBytes, base + 120)
                return DirEntry(entryName, type, startSector.toInt(), size)
            }
        }
        return null
    }

    private fun sectorOffset(bytes: ByteArray, sectorSize: Int, sector: Int, headerSize: Int): Int {
        if (sector < 0) throw YimuFormatException("Invalid sector $sector")
        val offset = headerSize + sector.toLong() * sectorSize
        if (offset < 0 || offset + sectorSize > bytes.size) throw YimuFormatException("Sector $sector out of bounds")
        return offset.toInt()
    }

    private fun hasMagic(bytes: ByteArray): Boolean {
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return false
        return true
    }

    private fun littleU16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) throw YimuFormatException("Truncated at $offset")
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun littleU32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) throw YimuFormatException("Truncated at $offset")
        val value = (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
        return value and 0xFFFFFFFFL
    }

    private fun decodeUtf16(bytes: ByteArray, offset: Int, length: Int): String {
        if (length <= 0) return ""
        val chars = CharArray(length / 2)
        for (i in chars.indices) {
            chars[i] = (
                (bytes[offset + i * 2].toInt() and 0xff) or
                    ((bytes[offset + i * 2 + 1].toInt() and 0xff) shl 8)
                ).toChar()
        }
        return String(chars)
    }
}