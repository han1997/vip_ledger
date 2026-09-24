package com.han1997.vipledger.core.model.time

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DateRangeTest {

    @Test
    fun dayCountIsInclusive() {
        val range = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        assertEquals(30, range.dayCount)
        assertEquals(1, DateRange.singleDay(LocalDate(2026, 9, 1)).dayCount)
        assertEquals(1, DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 1, 1)).dayCount)
    }

    @Test
    fun containsUsesClosedInterval() {
        val range = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        assertTrue(LocalDate(2026, 9, 1) in range)
        assertTrue(LocalDate(2026, 9, 30) in range)
        assertTrue(LocalDate(2026, 9, 15) in range)
        assertFalse(LocalDate(2026, 8, 31) in range)
        assertFalse(LocalDate(2026, 10, 1) in range)
    }

    @Test
    fun reversedRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            DateRange(LocalDate(2026, 9, 30), LocalDate(2026, 9, 1))
        }
    }
}
