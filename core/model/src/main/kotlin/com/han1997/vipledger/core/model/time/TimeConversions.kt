@file:OptIn(ExperimentalTime::class)

package com.han1997.vipledger.core.model.time

import kotlin.time.ExperimentalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * 时间语义换算（docs/DATA-MODEL.md 第 1 节）：
 * - `*_at` 列存「时刻」，用 UTC epoch millis，按设备时区归属到 [LocalDate]；
 * - `*_date` 列存「某天」，用 UTC 零点的 epoch millis，与时区无关。
 *
 * kotlinx-datetime 0.7 起 [Instant] 用 `kotlin.time.Instant`，在 Kotlin 2.2 上仍是实验 API，
 * 所以本文件整体 opt-in（见 docs/DEV-NOTES.md 第 3 节）。
 */
fun LocalDate.startOfDayEpochMillis(zone: TimeZone): Long = atStartOfDayIn(zone).toEpochMilliseconds()

fun Long.toLocalDate(zone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(zone).date

/** 日期列（`*_date`）的存取：固定按 UTC 零点，避免时区漂移。 */
fun LocalDate.toDateColumnEpochMillis(): Long =
    atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

fun Long.toDateColumnLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

/** `[startInclusive, endExclusive)` 的毫秒区间，可直接用于 SQL 的 `>= :from AND < :to`。 */
data class EpochMillisBounds(
    val startInclusive: Long,
    val endExclusive: Long,
) {
    operator fun contains(millis: Long): Boolean = millis >= startInclusive && millis < endExclusive
}

fun DateRange.toEpochMillisBounds(zone: TimeZone): EpochMillisBounds = EpochMillisBounds(
    startInclusive = start.startOfDayEpochMillis(zone),
    endExclusive = endInclusive.plus(1, DateTimeUnit.DAY).startOfDayEpochMillis(zone),
)