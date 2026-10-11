package com.han1997.vipledger.feature.ledger.ui.wealth.trend

import com.han1997.vipledger.core.domain.calculator.AssetHistoryCalculator
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 趋势页区间计算与 CSV 组装（P7）。 */
class AssetTrendUiStateTest {

    private val today = LocalDate(2026, 10, 11)
    private val earliest = LocalDate(2026, 6, 1)

    @Test
    fun range30DaysIncludesToday() {
        val range = trendDateRangeOf(TrendRange.LAST_30_DAYS, today, earliest)
        assertEquals(LocalDate(2026, 9, 12), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun range90DaysIncludesToday() {
        val range = trendDateRangeOf(TrendRange.LAST_90_DAYS, today, earliest)
        assertEquals(LocalDate(2026, 7, 14), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun thisYearCoversCalendarYear() {
        val range = trendDateRangeOf(TrendRange.THIS_YEAR, today, earliest)
        assertEquals(LocalDate(2026, 1, 1), range.start)
        assertEquals(LocalDate(2026, 12, 31), range.endInclusive)
    }

    @Test
    fun allStartsAtEarliestBaseline() {
        assertEquals(earliest..today, trendDateRangeOf(TrendRange.ALL, today, earliest).let { it.start..it.endInclusive })
    }

    @Test
    fun allWithoutBaselineFallsBackToToday() {
        val range = trendDateRangeOf(TrendRange.ALL, today, null)
        assertEquals(today, range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun pointAtIsBoundsChecked() {
        val point = AssetHistoryCalculator.Point(LocalDate(2026, 10, 1), 100L, 0L, 100L)
        val state = AssetTrendUiState(points = listOf(point))
        assertEquals(point, state.pointAt(0))
        assertNull(state.pointAt(1))
        assertNull(state.pointAt(null))
        assertNull(state.pointAt(-1))
    }

    @Test
    fun csvCarriesLedgerRangeBasisAndYuanRows() {
        val points = listOf(
            AssetHistoryCalculator.Point(LocalDate(2026, 10, 1), 100_00L, 0L, 100_00L),
            AssetHistoryCalculator.Point(LocalDate(2026, 10, 2), 70_00L, 30_00L, 40_00L),
        )
        val export = trendCsvOf(points, "默认账本", TrendRange.LAST_30_DAYS, today)
        assertTrue(export.fileName.startsWith("vipledger-trend-"), "文件名：${export.fileName}")
        assertTrue("-30d-20261011.csv" in export.fileName)
        val lines = export.csv.trim().lines()
        assertTrue(lines[0].startsWith("# vipledger asset trend"), "首行口径注释")
        assertTrue("ledger=默认账本" in lines[0])
        assertTrue("range=2026-10-01..2026-10-02" in lines[0])
        assertEquals("date,assets,debt,net_worth", lines[1])
        assertEquals("2026-10-01,100.00,0.00,100.00", lines[2])
        assertEquals("2026-10-02,70.00,30.00,40.00", lines[3])
    }
}