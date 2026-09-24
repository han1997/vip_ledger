package com.han1997.vipledger.core.model.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * 常见时间段（docs/DATA-MODEL.md 第 5 节），一周以周一为起始。
 *
 * 自定义时间段直接用 [DateRange] 表达，不占用预设枚举。
 */
enum class DateRangePreset {
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_QUARTER,
    LAST_QUARTER,
    THIS_YEAR,
    LAST_YEAR,
    ;

    /** 以 [today] 为基准算出闭区间。约定近 N 天包含今天。 */
    fun resolve(today: LocalDate): DateRange = when (this) {
        THIS_WEEK -> weekOf(today)
        LAST_WEEK -> weekOf(today.minus(7, DateTimeUnit.DAY))
        THIS_MONTH -> monthOf(today)
        LAST_MONTH -> monthOf(today.minus(1, DateTimeUnit.MONTH))
        LAST_7_DAYS -> DateRange(today.minus(6, DateTimeUnit.DAY), today)
        LAST_30_DAYS -> DateRange(today.minus(29, DateTimeUnit.DAY), today)
        THIS_QUARTER -> quarterOf(today)
        LAST_QUARTER -> {
            val start = startOfQuarter(today).minus(3, DateTimeUnit.MONTH)
            DateRange(start, endOfQuarter(today).minus(3, DateTimeUnit.MONTH))
        }
        THIS_YEAR -> DateRange(LocalDate(today.year, 1, 1), LocalDate(today.year, 12, 31))
        LAST_YEAR -> DateRange(LocalDate(today.year - 1, 1, 1), LocalDate(today.year - 1, 12, 31))
    }

    private fun weekOf(day: LocalDate): DateRange {
        val monday = startOfWeek(day)
        return DateRange(monday, monday.plus(6, DateTimeUnit.DAY))
    }

    private fun monthOf(day: LocalDate): DateRange =
        DateRange(startOfMonth(day), startOfMonth(day).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY))

    private fun quarterOf(day: LocalDate): DateRange = DateRange(startOfQuarter(day), endOfQuarter(day))

    private fun endOfQuarter(day: LocalDate): LocalDate =
        startOfQuarter(day).plus(3, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    companion object {
        /** 周一为一周起始；kotlinx-datetime 的 DayOfWeek 按 ISO 顺序声明，故 ordinal 即「周一起算的偏移」。 */
        fun startOfWeek(day: LocalDate): LocalDate = day.minus(day.dayOfWeek.ordinal, DateTimeUnit.DAY)

        fun startOfMonth(day: LocalDate): LocalDate = LocalDate(day.year, day.monthNumber, 1)

        fun startOfQuarter(day: LocalDate): LocalDate =
            LocalDate(day.year, ((day.monthNumber - 1) / 3) * 3 + 1, 1)
    }
}