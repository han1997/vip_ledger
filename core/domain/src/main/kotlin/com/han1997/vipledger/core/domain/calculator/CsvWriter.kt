package com.han1997.vipledger.core.domain.calculator

/**
 * 最小 CSV 生成器（RFC 4180 的保守子集）。
 *
 * - 字段含逗号、双引号、换行时用双引号包裹，内部双引号翻倍；
 * - 行尾固定 `\r\n`（Excel 与多数导入工具都认）；
 * - [BOM] 只在文件开头写一次，让 Excel 正确识别 UTF-8。
 */
object CsvWriter {

    const val BOM: Char = '\uFEFF'

    private const val LINE_SEPARATOR = "\r\n"

    private val SPECIAL = charArrayOf(',', '"', '\r', '\n')

    fun escape(field: String): String {
        val needsQuotes = field.any { it in SPECIAL }
        if (!needsQuotes) return field
        return '"' + field.replace("\"", "\"\"") + '"'
    }

    /** [rows] 全部行；[includeBom] 一般只在写文件时为 true。 */
    fun write(rows: List<List<String>>, includeBom: Boolean = false): String {
        val builder = StringBuilder()
        if (includeBom) builder.append(BOM)
        rows.forEachIndexed { index, row ->
            if (index > 0) builder.append(LINE_SEPARATOR)
            row.forEachIndexed { cellIndex, cell ->
                if (cellIndex > 0) builder.append(',')
                builder.append(escape(cell))
            }
        }
        return builder.toString()
    }
}