package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * 周期规则推算（docs/DATA-MODEL.md 3.6）。
 *
 * 设计要点：
 * - **按序号推算**：第 n 期直接由「锚点 + n 个步长」算出，而不是在上一期上反复加，
 *   这样「每月 31 号」在 2 月落到 28/29 后，3 月仍会回到 31 号，不会一路漂移。
 * - **锚点** 是第一个不早于起始日、且满足 `byWeekday` / `byMonthDay` / `byMonth` 的日子。
 * - 引擎用规则上的 `generatedCount` 做游标，[dueOccurrences] 只算「还欠几期没生成」，
 *   重复运行结果一致（幂等）。
 */
object RecurrenceCalculator {

    /** 单次补记的上限：长时间没打开 App 时避免一次生成成百上千笔。 */
    const val MAX_CATCH_UP: Int = 120

    private const val MONTHS_PER_YEAR = 12

    fun startDate(rule: RecurringRule): LocalDate = rule.startAt.toDateColumnLocalDate()

    /** 规则锚点 = 第 0 期所在日期。 */
    fun anchor(rule: RecurringRule): LocalDate {
        val start = startDate(rule)
        return when (rule.freq) {
            RecurrenceFrequency.DAILY, RecurrenceFrequency.EVERY_N_DAYS -> start
            RecurrenceFrequency.WEEKLY -> {
                val weekday = rule.byWeekday
                if (weekday == null) start else start.plus(daysUntilWeekday(start, weekday), DateTimeUnit.DAY)
            }
            RecurrenceFrequency.MONTHLY, RecurrenceFrequency.EVERY_N_MONTHS -> {
                val day = rule.byMonthDay ?: start.dayOfMonth
                val candidate = dateInMonth(start.year, start.monthNumber, day)
                if (candidate < start) dateInMonthFromIndex(monthIndexOf(start) + 1, day) else candidate
            }
            RecurrenceFrequency.YEARLY -> {
                val month = rule.byMonth ?: start.monthNumber
                val day = rule.byMonthDay ?: start.dayOfMonth
                val candidate = dateInMonth(start.year, month, day)
                if (candidate < start) dateInMonth(start.year + 1, month, day) else candidate
            }
        }
    }

    /** 第 [index] 期（从 0 数起）的日期。 */
    fun occurrenceAt(rule: RecurringRule, index: Int): LocalDate {
        require(index >= 0) { "期数序号不能为负：$index" }
        val interval = rule.interval.coerceAtLeast(1)
        val base = anchor(rule)
        return when (rule.freq) {
            RecurrenceFrequency.DAILY -> base.plus(index * interval, DateTimeUnit.DAY)
            RecurrenceFrequency.EVERY_N_DAYS -> base.plus(index * interval, DateTimeUnit.DAY)
            RecurrenceFrequency.WEEKLY -> base.plus(index * interval * DAYS_PER_WEEK, DateTimeUnit.DAY)
            RecurrenceFrequency.MONTHLY, RecurrenceFrequency.EVERY_N_MONTHS -> {
                val day = rule.byMonthDay ?: startDate(rule).dayOfMonth
                dateInMonthFromIndex(monthIndexOf(base) + index * interval, day)
            }
            RecurrenceFrequency.YEARLY -> {
                val month = rule.byMonth ?: base.monthNumber
                val day = rule.byMonthDay ?: base.dayOfMonth
                dateInMonth(base.year + index * interval, month, day)
            }
        }
    }

    /** 第 [index] 期是否还在结束条件之内（`COUNT` 按序号判，`UNTIL_DATE` 按日期判）。 */
    fun isWithinEnd(rule: RecurringRule, index: Int, date: LocalDate): Boolean = when (rule.endType) {
        RecurrenceEndType.NEVER -> true
        RecurrenceEndType.UNTIL_DATE -> rule.endDate?.let { date <= it.toDateColumnLocalDate() } ?: false
        RecurrenceEndType.COUNT -> rule.maxOccurrences?.let { index < it } ?: false
    }

    /**
     * 从规则自己的游标（`generatedCount`）出发，算出到今天为止所有应生成但还没生成的日期，
     * 升序返回；超过 [cap] 期时截断（长时间没打开 App 的保护）。
     */
    fun dueOccurrences(rule: RecurringRule, today: LocalDate, cap: Int = MAX_CATCH_UP): List<LocalDate> {
        if (!rule.enabled) return emptyList()
        val result = ArrayList<LocalDate>()
        var index = rule.generatedCount.coerceAtLeast(0)
        while (result.size < cap) {
            val date = occurrenceAt(rule, index)
            if (!isWithinEnd(rule, index, date)) break
            if (date > today) break
            result += date
            index++
        }
        return result
    }

    /**
     * 写回 `next_occurrence_at` 用：第 [fromIndex] 期还在结束条件内就返回它，
     * 否则返回 null（表示规则已经走完，不再有下一期）。
     */
    fun nextOccurrenceDate(rule: RecurringRule, fromIndex: Int): LocalDate? {
        val index = fromIndex.coerceAtLeast(0)
        val date = occurrenceAt(rule, index)
        return if (isWithinEnd(rule, index, date)) date else null
    }

    /** 兜底用：`byMonthDay = 31` 或起始日超出当月天数时取当月最后一天。 */
    fun dateInMonth(year: Int, month: Int, day: Int): LocalDate =
        LocalDate(year, month, day.coerceIn(1, daysInMonth(year, month)))

    fun daysInMonth(year: Int, month: Int): Int =
        LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY).dayOfMonth

    private fun monthIndexOf(date: LocalDate): Int = date.year * MONTHS_PER_YEAR + (date.monthNumber - 1)

    private fun dateInMonthFromIndex(monthIndex: Int, day: Int): LocalDate {
        val year = monthIndex.floorDiv(MONTHS_PER_YEAR)
        val month = monthIndex.mod(MONTHS_PER_YEAR) + 1
        return dateInMonth(year, month, day)
    }

    /** `byWeekday` 存的是「0 = 周一 … 6 = 周日」，而 kotlinx-datetime 的 `isoDayNumber` 是 1–7。 */
    private fun daysUntilWeekday(from: LocalDate, weekday: Int): Int =
        (weekday + 1 - from.dayOfWeek.isoDayNumber + DAYS_PER_WEEK) % DAYS_PER_WEEK

    private const val DAYS_PER_WEEK = 7
}
