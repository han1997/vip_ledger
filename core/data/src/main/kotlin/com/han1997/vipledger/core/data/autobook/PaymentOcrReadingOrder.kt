package com.han1997.vipledger.core.data.autobook

import kotlin.math.abs
import kotlin.math.min

internal data class PaymentOcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY get() = (top.toLong() + bottom) / 2.0
    val height get() = (bottom - top).coerceAtLeast(1)
}

/** Restore label/value rows when ML Kit returns separate left/right text blocks. */
internal object PaymentOcrReadingOrder {
    fun format(lines: List<PaymentOcrLine>): String {
        val rows = mutableListOf<MutableList<PaymentOcrLine>>()
        lines.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.centerY }, { it.left })).forEach { line ->
            val row = rows.lastOrNull()
            val anchor = row?.firstOrNull()
            if (anchor != null && abs(line.centerY - anchor.centerY) <= min(line.height, anchor.height) / 2.0) {
                row.add(line)
            } else rows.add(mutableListOf(line))
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }
}
