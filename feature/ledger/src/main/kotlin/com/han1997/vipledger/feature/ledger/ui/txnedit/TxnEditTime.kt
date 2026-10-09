@file:OptIn(ExperimentalTime::class)

package com.han1997.vipledger.feature.ledger.ui.txnedit

import com.han1997.vipledger.core.model.time.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal fun replaceEntryDate(millis: Long, date: LocalDate, zone: TimeZone): Long =
    LocalDateTime(date, millis.toLocalDateTime(zone).time).toInstant(zone).toEpochMilliseconds()

internal fun replaceEntryTime(millis: Long, time: LocalTime, zone: TimeZone): Long =
    LocalDateTime(millis.toLocalDateTime(zone).date, time).toInstant(zone).toEpochMilliseconds()

internal fun parsedEntryTime(millis: Long, date: LocalDate, time: LocalTime?, zone: TimeZone): Long =
    LocalDateTime(date, time ?: millis.toLocalDateTime(zone).time).toInstant(zone).toEpochMilliseconds()

internal fun parseEntryClock(hour: String, minute: String, second: String): LocalTime? {
    if (listOf(hour, minute, second).any { it.isEmpty() || it.length > 2 || it.any { ch -> ch !in '0'..'9' } }) return null
    return runCatching { LocalTime(hour.toInt(), minute.toInt(), second.toInt()) }.getOrNull()
}
