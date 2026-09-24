package com.han1997.vipledger.core.domain.calculator

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvWriterTest {

    @Test
    fun leavesPlainFieldsUntouched() {
        assertEquals("午餐", CsvWriter.escape("午餐"))
        assertEquals("plain text", CsvWriter.escape("plain text"))
    }

    @Test
    fun quotesFieldsWithCommaQuoteOrNewline() {
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvWriter.escape("line1\nline2"))
        assertEquals("\"line1\r\nline2\"", CsvWriter.escape("line1\r\nline2"))
    }

    @Test
    fun joinsRowsWithCrLfAndCommas() {
        val text = CsvWriter.write(listOf(listOf("a", "b"), listOf("c", "d")))
        assertEquals("a,b\r\nc,d", text)
    }

    @Test
    fun bomIsOnlyWrittenWhenAsked() {
        val rows = listOf(listOf("日期"))
        assertEquals("日期", CsvWriter.write(rows))
        assertEquals("\uFEFF日期", CsvWriter.write(rows, includeBom = true))
    }

    @Test
    fun keepsEmptyFieldsEmpty() {
        assertEquals("a,,c", CsvWriter.write(listOf(listOf("a", "", "c"))))
    }
}