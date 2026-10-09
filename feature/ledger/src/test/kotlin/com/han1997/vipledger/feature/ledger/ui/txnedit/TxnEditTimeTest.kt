@file:OptIn(ExperimentalTime::class)

package com.han1997.vipledger.feature.ledger.ui.txnedit

import com.han1997.vipledger.core.model.time.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * 记账时间换算：改日期保留时分秒、手动改时间保留日期、AI 识别的时刻优先于表单时刻。
 * 这三条是「时间精确到秒」需求的纯逻辑回归（见 10-09-txn-editor-polish 的 prd.md）。
 */
class TxnEditTimeTest {

    private val zone = TimeZone.of("Asia/Shanghai")

    @Test
    fun replacingDateKeepsTimeOfDayAndMilliseconds() {
        val original = millis(2026, 9, 24, 12, 30, 45, 321_000_000)
        val updated = replaceEntryDate(original, LocalDate(2026, 9, 1), zone)
        assertEquals(LocalDateTime(2026, 9, 1, 12, 30, 45, 321_000_000), updated.toLocalDateTime(zone))
    }

    @Test
    fun replacingTimeKeepsTheDate() {
        val original = millis(2026, 9, 24, 12, 30, 45, 321_000_000)
        val updated = replaceEntryTime(original, LocalTime(8, 5, 9), zone)
        assertEquals(LocalDateTime(2026, 9, 24, 8, 5, 9, 0), updated.toLocalDateTime(zone))
    }

    @Test
    fun parsedTimeWinsOverFormTime() {
        val formTime = millis(2026, 9, 24, 12, 30, 45, 0)
        val updated = parsedEntryTime(formTime, LocalDate(2026, 9, 20), LocalTime(7, 1, 2), zone)
        assertEquals(LocalDateTime(2026, 9, 20, 7, 1, 2, 0), updated.toLocalDateTime(zone))
    }

    @Test
    fun missingParsedTimeFallsBackToFormTime() {
        val formTime = millis(2026, 9, 24, 12, 30, 45, 0)
        val updated = parsedEntryTime(formTime, LocalDate(2026, 9, 20), null, zone)
        assertEquals(LocalDateTime(2026, 9, 20, 12, 30, 45, 0), updated.toLocalDateTime(zone))
    }

    @Test
    fun parseEntryClockAcceptsValidParts() {
        assertEquals(LocalTime(0, 0, 0), parseEntryClock("0", "0", "0"))
        assertEquals(LocalTime(23, 59, 59), parseEntryClock("23", "59", "59"))
    }

    @Test
    fun parseEntryClockRejectsGarbageAndOutOfRange() {
        assertNull(parseEntryClock("", "0", "0"))
        assertNull(parseEntryClock("24", "0", "0"))
        assertNull(parseEntryClock("0", "60", "0"))
        assertNull(parseEntryClock("0", "0", "60"))
        assertNull(parseEntryClock("0", "0", "000"))
        assertNull(parseEntryClock("aa", "0", "0"))
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        nanosecond: Int,
    ): Long = LocalDateTime(year, month, day, hour, minute, second, nanosecond)
        .toInstant(zone)
        .toEpochMilliseconds()
}
