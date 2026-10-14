package com.han1997.vipledger.core.data.importexport.yimu

import java.nio.charset.Charset

/**
 * BIFF8 单元格值。本层刻意只输出「字符串 / Double / 空白」三种:
 * 日期序列号在 BIFF8 里就是 [Number],是否按日期解释要到落库映射层、结合格式串决定,
 * 因此这里不引入 `java.time`(项目未开启 core library desugaring)。
 */
sealed interface BiffCell {
    data class Text(val value: String) : BiffCell
    data class Number(val value: Double) : BiffCell
    data object Blank : BiffCell
}

/**
 * 一张工作表的单元格矩阵。外层是行(按行号升序,包含表头行 0),
 * 内层是列;不存在的单元格为 `null`,`Blank` 记录为 [BiffCell.Blank]。
 */
class BiffSheet(val rows: List<List<BiffCell?>>)

/**
 * BIFF8 `Workbook` 流 → 第一张(也是唯一)worksheet 的单元格矩阵。
 *
 * 只解析账单需要的记录:BOF / EOF / BOUNDSHEET / SST + CONTINUE / LABELSST / NUMBER /
 * RK / MULRK / ROW / BLANK / MULBLANK;其余按 payload 长度跳过。
 *
 * 关键实现约定(见 `.trellis/spec/android/import-export.md`):
 * - **SST 跨 CONTINUE 续接**:每块 payload 上限 8224 字节,切开处会重写一个 options 字节;
 *   维护 `(chunkIndex, offset)` 游标,越界时跳到下一块偏移 0 **读一个字节**当新 options,
 *   绝不能把 payload 简单 concat。rich-text 段数组与 Far-East `cbext` 块各自独立跳过,不再读 options。
 * - 字符串头 = `cch`(u16)+ 标志字节:bit0 = `fHighByte`(1 ⇒ UTF-16LE 每字符 2 字节;
 *   0 ⇒ CP1252 每字符 1 字节);bit3 = rich-text 段(先 u16 `nrun`,再 `4*nrun` 字节跳过);
 *   bit2 = Far-East(`cbext` u32 + `cbext` 字节跳过)。跳过顺序:字符 → rich-text → Far-East。
 * - 0 元 / 越界一律抛 [YimuFormatException],不 OOM。
 */
object Biff8Reader {

    // ---- BIFF8 记录 id ----
    private const val BOF = 0x0809
    private const val EOF = 0x000A
    private const val BOUNDSHEET = 0x0085
    private const val SST = 0x00FC
    private const val CONTINUE = 0x003C
    private const val LABELSST = 0x00FD
    private const val NUMBER = 0x0203
    private const val RK = 0x027E
    private const val MULRK = 0x00BD
    private const val ROW = 0x0208
    private const val BLANK = 0x0201
    private const val MULBLANK = 0x00BE
    private const val DIMENSIONS = 0x0200

    /** BOF payload 偏移 2 的子流类型:globals 与 worksheet。 */
    private const val SUBSTREAM_GLOBALS = 0x0005
    private const val SUBSTREAM_WORKSHEET = 0x0010

    private const val MAX_CELLS = 20_000_000

    private val CP1252: Charset = Charset.forName("windows-1252")

    private data class Record(val id: Int, val offset: Int, val length: Int)

    /**
     * 解析 `Workbook` 流字节,返回第一张 worksheet 的矩阵。
     *
     * @throws YimuFormatException 记录截断 / SST 越界 / 单元格越界。
     */
    fun readSheet(workbookStream: ByteArray): BiffSheet {
        val records = scanRecords(workbookStream)

        val sst = readSst(workbookStream, records)

        // 第一张 worksheet 子流:从它的 BOF 扫到对应 EOF。
        val worksheetStart = records.indexOfFirst { it.id == BOF && substreamType(workbookStream, it) == SUBSTREAM_WORKSHEET }
        if (worksheetStart < 0) throw YimuFormatException("No worksheet substream in workbook")

        val cells = HashMap<Long, BiffCell>()
        var maxRow = -1
        var maxCol = -1

        var index = worksheetStart + 1
        while (index < records.size) {
            val record = records[index]
            if (record.id == EOF) break
            when (record.id) {
                ROW, DIMENSIONS -> { /* 仅用于元数据;单元格行号来自记录自身的 rw 字段 */ }
                LABELSST -> {
                    requireLength(record, 10, "LABELSST")
                    val row = u16(workbookStream, record.offset)
                    val col = u16(workbookStream, record.offset + 2)
                    val sstIndex = u32(workbookStream, record.offset + 6)
                    if (sstIndex < 0 || sstIndex >= sst.size) {
                        throw YimuFormatException("SST index $sstIndex out of range (${sst.size})")
                    }
                    put(cells, row, col) { BiffCell.Text(sst[sstIndex]) }
                    if (row > maxRow) maxRow = row
                    if (col > maxCol) maxCol = col
                }
                NUMBER -> {
                    requireLength(record, 14, "NUMBER")
                    val row = u16(workbookStream, record.offset)
                    val col = u16(workbookStream, record.offset + 2)
                    val value = doubleAt(workbookStream, record.offset + 6)
                    put(cells, row, col) { BiffCell.Number(value) }
                    if (row > maxRow) maxRow = row
                    if (col > maxCol) maxCol = col
                }
                RK -> {
                    requireLength(record, 10, "RK")
                    val row = u16(workbookStream, record.offset)
                    val col = u16(workbookStream, record.offset + 2)
                    val value = rkToDouble(u32(workbookStream, record.offset + 6))
                    put(cells, row, col) { BiffCell.Number(value) }
                    if (row > maxRow) maxRow = row
                    if (col > maxCol) maxCol = col
                }
                MULRK -> {
                    val row = u16(workbookStream, record.offset)
                    val count = (record.length - 6) / 6
                    for (i in 0 until count) {
                        val entry = record.offset + 4 + i * 6
                        if (entry + 6 > record.offset + record.length) break
                        val col = u16(workbookStream, record.offset + 2) + i
                        val value = rkToDouble(u32(workbookStream, entry + 2))
                        put(cells, row, col) { BiffCell.Number(value) }
                        if (col > maxCol) maxCol = col
                    }
                    if (row > maxRow) maxRow = row
                }
                BLANK -> {
                    requireLength(record, 6, "BLANK")
                    val row = u16(workbookStream, record.offset)
                    val col = u16(workbookStream, record.offset + 2)
                    put(cells, row, col) { BiffCell.Blank }
                    if (row > maxRow) maxRow = row
                    if (col > maxCol) maxCol = col
                }
                MULBLANK -> {
                    val row = u16(workbookStream, record.offset)
                    val count = (record.length - 6) / 2
                    for (i in 0 until count) {
                        val col = u16(workbookStream, record.offset + 2) + i
                        put(cells, row, col) { BiffCell.Blank }
                        if (col > maxCol) maxCol = col
                    }
                    if (row > maxRow) maxRow = row
                }
                else -> Unit
            }
            index++
        }

        val rowCount = maxRow + 1
        val colCount = maxCol + 1
        if (rowCount <= 0 || colCount <= 0) return BiffSheet(emptyList())
        if (rowCount.toLong() * colCount > MAX_CELLS) throw YimuFormatException("Sheet too large ($rowCount x $colCount)")

        val rows = ArrayList<List<BiffCell?>>(rowCount)
        for (r in 0 until rowCount) {
            val row = arrayOfNulls<BiffCell?>(colCount)
            for (c in 0 until colCount) row[c] = cells[keyOf(r, c)]
            rows.add(row.toList())
        }
        return BiffSheet(rows)
    }

    // ---- SST ----

    private fun readSst(stream: ByteArray, records: List<Record>): List<String> {
        val sstIndex = records.indexOfFirst { it.id == SST }
        if (sstIndex < 0) return emptyList()
        val first = records[sstIndex]
        if (first.length < 8) throw YimuFormatException("SST record too short")

        // SST 之后紧密相接的 CONTINUE 都是它的续块。
        val chunks = ArrayList<ByteArray>()
        chunks.add(stream.copyOfRange(first.offset, first.offset + first.length))
        var index = sstIndex + 1
        while (index < records.size && records[index].id == CONTINUE) {
            val cont = records[index]
            chunks.add(stream.copyOfRange(cont.offset, cont.offset + cont.length))
            index++
        }

        val total = u32(chunks[0], 0)
        val unique = u32(chunks[0], 4)
        if (unique < 0 || unique > MAX_CELLS) throw YimuFormatException("SST unique count $unique out of range")

        val cursor = ChunkCursor(chunks, 8)
        val strings = ArrayList<String>(unique)
        for (i in 0 until unique) {
            val cch = cursor.u16()
            val grbit = cursor.u8()
            strings.add(cursor.readChars(cch, grbit))
        }
        // `total` 含共享引用计数,唯一字符串数才是我们要的。
        @Suppress("UNUSED_VARIABLE")
        val ignored = total
        return strings
    }

    /**
     * 跨 CONTINUE 的游标。字符读取与原始跳过是两条不同规则:
     * - [readChars] 越界时在下一块偏移 0 读一个 options 字节;
     * - [raw] 越界时直接对齐到下一块偏移 0(rich-text / Far-East 块不重读 options)。
     */
    private class ChunkCursor(private val chunks: List<ByteArray>, private var offset: Int) {
        private var chunkIndex = 0

        fun u8(): Int = raw(1)[0].toInt() and 0xff

        fun u16(): Int {
            val b = raw(2)
            return (b[0].toInt() and 0xff) or ((b[1].toInt() and 0xff) shl 8)
        }

        fun u32(): Int {
            val b = raw(4)
            return (b[0].toInt() and 0xff) or
                ((b[1].toInt() and 0xff) shl 8) or
                ((b[2].toInt() and 0xff) shl 16) or
                ((b[3].toInt() and 0xff) shl 24)
        }

        /** 原样抽取 [count] 字节,跨块时对齐到下一块偏移 0(不读 options)。 */
        fun raw(count: Int): ByteArray {
            if (count < 0) throw YimuFormatException("Negative read $count")
            val out = ByteArray(count)
            var written = 0
            while (written < count) {
                advanceIfExhausted()
                val chunk = chunks[chunkIndex]
                val take = minOf(count - written, chunk.size - offset)
                if (take <= 0) throw YimuFormatException("SST cursor made no progress")
                System.arraycopy(chunk, offset, out, written, take)
                offset += take
                written += take
            }
            return out
        }

        /**
         * 读一个 BIFF8 字符串的字符部分。跨越 CONTINUE 时先在下一块偏移 0 读一个
         * options 字节(只有 bit0 = fHighByte 生效);宽字符且旧块末尾只剩 1 字节时,
         * 那 1 字节直接丢弃(既非填充也非内容)。
         */
        fun readChars(cch: Int, grbit: Int): String {
            val nrun = if (grbit and 0x08 != 0) u16() else 0
            val cbext = if (grbit and 0x04 != 0) u32() else 0
            var wide = grbit and 0x01 != 0

            val builder = StringBuilder(cch)
            var remaining = cch
            while (remaining > 0) {
                if (offset >= chunks[chunkIndex].size) {
                    chunkIndex++
                    if (chunkIndex >= chunks.size) {
                        throw YimuFormatException("SST character data truncated")
                    }
                    offset = 1
                    wide = chunks[chunkIndex][0].toInt() and 0x01 != 0
                }
                val chunk = chunks[chunkIndex]
                val available = chunk.size - offset
                if (wide && available == 1) {
                    offset += 1
                    continue
                }
                if (available <= 0) continue
                if (wide) {
                    val take = minOf(remaining, available / 2)
                    if (take <= 0) throw YimuFormatException("SST cursor made no progress")
                    builder.append(String(chunk, offset, take * 2, Charsets.UTF_16LE))
                    offset += take * 2
                    remaining -= take
                } else {
                    val take = minOf(remaining, available)
                    builder.append(String(chunk, offset, take, CP1252))
                    offset += take
                    remaining -= take
                }
            }

            // rich-text 段数组(4 字节/段)与 Far-East 扩展块各自跳过。
            if (nrun > 0) raw(4 * nrun)
            if (cbext > 0) raw(cbext)
            return builder.toString()
        }

        private fun advanceIfExhausted() {
            while (chunkIndex < chunks.size && offset >= chunks[chunkIndex].size) {
                chunkIndex++
                offset = 0
            }
            if (chunkIndex >= chunks.size) throw YimuFormatException("SST structure truncated")
        }
    }

    // ---- 记录扫描 ----

    private fun scanRecords(stream: ByteArray): List<Record> {
        val records = ArrayList<Record>()
        var position = 0
        while (position + 4 <= stream.size) {
            val id = u16(stream, position)
            val length = u16(stream, position + 2)
            val payload = position + 4
            if (payload + length > stream.size) throw YimuFormatException("Record 0x%04x truncated".format(id))
            records.add(Record(id, payload, length))
            position = payload + length
        }
        return records
    }

    private fun substreamType(stream: ByteArray, record: Record): Int {
        if (record.length < 4) return -1
        return u16(stream, record.offset + 2)
    }

    private fun requireLength(record: Record, minimum: Int, name: String) {
        if (record.length < minimum) throw YimuFormatException("$name record too short (${record.length} < $minimum)")
    }

    private inline fun put(cells: HashMap<Long, BiffCell>, row: Int, col: Int, value: () -> BiffCell) {
        cells[keyOf(row, col)] = value()
    }

    private fun keyOf(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)

    // ---- RK 解码 ----

    /**
     * RK 值低 2 位决定类型:bit0 = 1 ⇒ 30 位整数(算术右移 2 位并符号扩展);
     * bit0 = 0 ⇒ IEEE754 double 的高 30 位(补 2 个 0 字节后按 double 解);bit1 = 1 ⇒ 结果除以 100。
     */
    private fun rkToDouble(rk: Int): Double {
        val base = if (rk and 0x02 != 0) {
            val shifted = rk shr 2
            val signExtended = if (shifted and 0x20000000 != 0) shifted or 0xC0000000.toInt() else shifted
            signExtended.toDouble()
        } else {
            val bits = (rk.toLong() and 0xFFFFFFFFL) and 0xFFFFFFFCL
            Double.fromBits(bits shl 32)
        }
        return if (rk and 0x01 != 0) base / 100.0 else base
    }

    // ---- 小端读取 ----

    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) throw YimuFormatException("Truncated read at $offset")
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun u32(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) throw YimuFormatException("Truncated read at $offset")
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun doubleAt(bytes: ByteArray, offset: Int): Double {
        if (offset < 0 || offset + 8 > bytes.size) throw YimuFormatException("Truncated double at $offset")
        var bits = 0L
        for (i in 7 downTo 0) {
            bits = (bits shl 8) or (bytes[offset + i].toLong() and 0xff)
        }
        return Double.fromBits(bits)
    }
}