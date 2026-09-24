package com.han1997.vipledger.core.model.time

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateRangePresetTest {

    /** 2026-09-24 是周四，用来覆盖「今天在周中」的情况。 */
    private val thursday = LocalDate(2026, 9, 24)

    @Test
    fun weekPresetsStartOnMonday() {
        assertEquals(DateRange(LocalDate(2026, 9, 21), LocalDate(2026, 9, 27)), DateRangePreset.THIS_WEEK.resolve(thursday))
        assertEquals(DateRange(LocalDate(2026, 9, 14), LocalDate(2026, 9, 20)), DateRangePreset.LAST_WEEK.resolve(thursday))
        val monday = LocalDate(2026, 9, 21)
        assertEquals(DateRange(monday, LocalDate(2026, 9, 27)), DateRangePreset.THIS_WEEK.resolve(monday))
        assertEquals(DateRange(monday, LocalDate(2026, 9, 27)), DateRangePreset.THIS_WEEK.resolve(LocalDate(2026, 9, 27)))
        assertEquals(
            DateRange(LocalDate(2026, 9, 28), LocalDate(2026, 10, 4)),
            DateRangePreset.THIS_WEEK.resolve(LocalDate(2026, 10, 3)),
        )
    }

    @Test
    fun monthPresetsCoverWholeMonthsIncludingLeapAndShortMonths() {
        assertEquals(DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), DateRangePreset.THIS_MONTH.resolve(thursday))
        assertEquals(DateRange(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31)), DateRangePreset.LAST_MONTH.resolve(thursday))
        assertEquals(
            DateRange(LocalDate(2027, 2, 1), LocalDate(2027, 2, 28)),
            DateRangePreset.THIS_MONTH.resolve(LocalDate(2027, 2, 14)),
        )
        assertEquals(
            DateRange(LocalDate(2024, 2, 1), LocalDate(2024, 2, 29)),
            DateRangePreset.THIS_MONTH.resolve(LocalDate(2024, 2, 14)),
        )
        assertEquals(
            DateRange(LocalDate(2026, 12, 1), LocalDate(2026, 12, 31)),
            DateRangePreset.LAST_MONTH.resolve(LocalDate(2027, 1, 3)),
        )
    }

    @Test
    fun rollingWindowPresetsIncludeToday() {
        assertEquals(DateRange(LocalDate(2026, 9, 18), thursday), DateRangePreset.LAST_7_DAYS.resolve(thursday))
        assertEquals(DateRange(LocalDate(2026, 8, 26), thursday), DateRangePreset.LAST_30_DAYS.resolve(thursday))
        assertEquals(7, DateRangePreset.LAST_7_DAYS.resolve(thursday).dayCount)
        assertEquals(30, DateRangePreset.LAST_30_DAYS.resolve(thursday).dayCount)
    }

    @Test
    fun quarterPresetsMatchCalendarBoundaries() {
        assertEquals(DateRange(LocalDate(2026, 7, 1), LocalDate(2026, 9, 30)), DateRangePreset.THIS_QUARTER.resolve(thursday))
        assertEquals(DateRange(LocalDate(2026, 4, 1), LocalDate(2026, 6, 30)), DateRangePreset.LAST_QUARTER.resolve(thursday))
        assertEquals(
            DateRange(LocalDate(2025, 10, 1), LocalDate(2025, 12, 31)),
            DateRangePreset.LAST_QUARTER.resolve(LocalDate(2026, 2, 1)),
        )
        assertEquals(DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 12, 31)), DateRangePreset.THIS_YEAR.resolve(thursday))
        assertEquals(DateRange(LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)), DateRangePreset.LAST_YEAR.resolve(thursday))
    }
}