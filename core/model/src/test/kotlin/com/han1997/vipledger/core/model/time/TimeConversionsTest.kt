package com.han1997.vipledger.core.model.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeConversionsTest {

    private val shanghai = TimeZone.of("Asia/Shanghai")

    @Test
    fun deviceZoneRoundTripsAreStable() {
        val start = LocalDate(2026, 9, 24).startOfDayEpochMillis(shanghai)
        assertEquals(LocalDate(2026, 9, 24), start.toLocalDate(shanghai))
        assertEquals(LocalDate(2026, 9, 23), (start - 1).toLocalDate(shanghai))
    }

    @Test
    fun dateColumnUsesUtcMidnight() {
        val millis = LocalDate(2026, 9, 24).toDateColumnEpochMillis()
        assertEquals(1790208000000L, millis)
        assertEquals(LocalDate(2026, 9, 24), millis.toDateColumnLocalDate())
        assertEquals(20720L, LocalDate(2026, 9, 24).toEpochDays())
    }

    @Test
    fun rangeBoundsAreHalfOpenOnTheDeviceZone() {
        val bounds = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)).toEpochMillisBounds(shanghai)
        val firstDay = LocalDate(2026, 9, 1).startOfDayEpochMillis(shanghai)
        val lastDay = LocalDate(2026, 9, 30).startOfDayEpochMillis(shanghai)
        assertTrue(firstDay in bounds)
        assertTrue(bounds.startInclusive in bounds)
        assertTrue(lastDay in bounds)
        assertFalse(bounds.endExclusive in bounds)
        assertFalse((firstDay - 1) in bounds)
    }

    @Test
    fun presetResolvesToBoundsCoveringItsOwnDays() {
        val range = DateRangePreset.THIS_MONTH.resolve(LocalDate(2026, 9, 24))
        val bounds = range.toEpochMillisBounds(shanghai)
        assertEquals(30 * 24 * 60 * 60 * 1000L, bounds.endExclusive - bounds.startInclusive)
    }
}