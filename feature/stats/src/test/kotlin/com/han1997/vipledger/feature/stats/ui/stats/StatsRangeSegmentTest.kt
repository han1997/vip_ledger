package com.han1997.vipledger.feature.stats.ui.stats

import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 统计页分段控件（本周 / 本月 / 本年 / 自定义）的选中项映射。
 * 有自定义区间时优先选「自定义」；其余预设（如「不限」）也归到「自定义」。
 */
class StatsRangeSegmentTest {

    private val range = DateRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 12))

    @Test
    fun weekMonthYearMapToTheirOwnSegments() {
        assertEquals(DateRangePreset.THIS_WEEK.name, rangeSegmentId(DateRangePreset.THIS_WEEK, null))
        assertEquals(DateRangePreset.THIS_MONTH.name, rangeSegmentId(DateRangePreset.THIS_MONTH, null))
        assertEquals(DateRangePreset.THIS_YEAR.name, rangeSegmentId(DateRangePreset.THIS_YEAR, null))
    }

    @Test
    fun customRangeWinsOverPreset() {
        assertEquals(CUSTOM_SEGMENT_ID, rangeSegmentId(DateRangePreset.THIS_MONTH, range))
    }

    @Test
    fun presetsOutsideTheSegmentListFallBackToCustom() {
        assertEquals(CUSTOM_SEGMENT_ID, rangeSegmentId(DateRangePreset.LAST_MONTH, null))
        assertEquals(CUSTOM_SEGMENT_ID, rangeSegmentId(null, null))
    }
}