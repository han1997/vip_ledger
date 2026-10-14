package com.han1997.vipledger.core.data.importexport.yimu

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * 一木记账 .xls 账单的入口解析器：接受裸 .xls（OLE2/CFB）或包裹它的 .zip，
 * 返回第一张工作表的单元格矩阵。
 *
 * 分派逻辑只看前 4 字节：
 * - `d0 cf 11 e0` → OLE2/CFB 容器（裸 .xls）；
 * - `50 4b 03 04` → zip 容器，取**第一个**名字以 `.xls` 结尾的成员；
 * - 其余 → 抛 [YimuFormatException]。
 *
 * zip 条目名含 `..` 或以 `/` 开头时拒绝（与 `SnapshotArchive` 的 zip-slip 防护同口径）。
 * 复用 `java.util.zip.ZipInputStream`，零新增依赖。
 */
object YimuBillParser {

    /** OLE2/CFB 魔数前 4 字节。 */
    private val OLE_HEAD: ByteArray = byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11.toByte(), 0xe0.toByte())

    /** zip 魔数前 4 字节（Local File Header）。 */
    private val ZIP_HEAD: ByteArray = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

    /**
     * 解析 [bytes]（裸 .xls 或包裹它的 .zip），返回第一张工作表的单元格矩阵。
     *
     * @throws YimuFormatException 坏魔数 / 结构截断 / zip-slip / 内部越界。
     */
    fun parse(bytes: ByteArray): BiffSheet {
        if (bytes.size < 4) throw YimuFormatException("Yimu file too short (${bytes.size} bytes)")
        val head = headOf(bytes)
        return when {
            head.contentEquals(OLE_HEAD) -> Biff8Reader.readSheet(Ole2Reader.readStream(bytes, WORKBOOK_STREAM))
            head.contentEquals(ZIP_HEAD) -> parseZip(bytes)
            else -> throw YimuFormatException("Unknown container magic (${head.hex()})")
        }
    }

    private fun parseZip(bytes: ByteArray): BiffSheet {
        return try {
            val stream = ZipInputStream(bytes.inputStream())
            var result: BiffSheet? = null
            stream.use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name.startsWith("/") || name.contains("..")) {
                        throw YimuFormatException("Unsafe zip entry name: $name")
                    }
                    if (name.lowercase().endsWith(".xls")) {
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                        val payload = out.toByteArray()
                        if (payload.size < 4) throw YimuFormatException("Zip member too short: $name")
                        result = Biff8Reader.readSheet(Ole2Reader.readStream(payload, WORKBOOK_STREAM))
                        break
                    }
                }
            }
            result ?: throw YimuFormatException("No .xls member found in zip")
        } catch (e: YimuFormatException) {
            throw e
        } catch (e: IOException) {
            throw YimuFormatException("Invalid zip container: ${e.message}")
        }
    }

    private fun headOf(b: ByteArray): ByteArray = b.copyOf(4)

    private fun ByteArray.hex(): String = joinToString(" ") { "%02x".format(it.toInt() and 0xff) }

    private const val WORKBOOK_STREAM = "Workbook"
}

/**
 * 一木账单解析/解包阶段的格式错误。继承 [IllegalArgumentException]，让上层（`LedgerImporter`）
 * 能区分「文件本身不是我认识的一木账单」与其他错误，而不会抛出 OOM / 无法捕获的底层异常。
 */
class YimuFormatException(message: String) : IllegalArgumentException(message)