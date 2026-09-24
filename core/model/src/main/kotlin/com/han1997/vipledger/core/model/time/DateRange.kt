package com.han1997.vipledger.core.model.time

import kotlinx.datetime.LocalDate

/**
 * 闭区间日期范围（含首尾），用于搜索与统计的时间条件。
 * 使用 [LocalDate]（设备时区的「某一天」语义），UTC epoch millis 的转换在数据层完成。
 */
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(start <= endInclusive) {
            "结束日期不能早于开始日期：" + start + " ~ " + endInclusive
        }
    }

    /** 包含的天数（闭区间）。 */
    val dayCount: Int
        get() = (endInclusive.toEpochDays() - start.toEpochDays() + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= endInclusive

    companion object {
        /** 单日范围。 */
        fun singleDay(date: LocalDate): DateRange = DateRange(date, date)
    }
}
