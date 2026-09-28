package com.han1997.vipledger.core.domain.nlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Span 表：高优先级先落，重叠者退让；掩码保持下标不变。 */
class SpanTableTest {

    @Test
    fun claimsNonOverlappingSpans() {
        val table = SpanTable("3月5日 房租 3500")
        assertTrue(table.tryClaim(Span(0, 4, SpanKind.DATE)))
        assertFalse(table.tryClaim(Span(2, 3, SpanKind.AMOUNT)), "重叠应退让")
        assertTrue(table.tryClaim(Span(5, 7, SpanKind.MERCHANT)))
    }

    @Test
    fun emptySpanIsRejected() {
        val table = SpanTable("abc")
        assertFalse(table.tryClaim(Span(1, 1, SpanKind.AMOUNT)))
    }

    @Test
    fun maskedPreservesIndices() {
        val text = "3月5日 房租 3500"
        val table = SpanTable(text)
        table.tryClaim(Span(0, 4, SpanKind.DATE))
        val masked = table.masked()
        assertEquals(text.length, masked.length)
        assertEquals("房租 3500", masked.replace('\u0000', ' ').trim())
    }

    @Test
    fun isTakenDetectsOverlap() {
        val table = SpanTable("hello")
        table.tryClaim(Span(1, 3, SpanKind.AMOUNT))
        assertTrue(table.isTaken(2, 4))
        assertFalse(table.isTaken(0, 1))
    }
}
