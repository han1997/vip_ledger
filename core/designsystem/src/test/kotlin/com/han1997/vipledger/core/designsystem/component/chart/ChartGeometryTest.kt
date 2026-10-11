package com.han1997.vipledger.core.designsystem.component.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChartGeometryTest {

    @Test
    fun sweepAnglesCoverFullCircleInProportion() {
        val sweeps = ChartGeometry.sweepAngles(listOf(1L, 1L, 2L))

        assertEquals(360f, sweeps.sum(), 0.01f)
        assertEquals(listOf(90f, 90f, 180f), sweeps)
    }

    @Test
    fun sweepAnglesAreAllZeroWhenTotalIsZero() {
        assertEquals(listOf(0f, 0f), ChartGeometry.sweepAngles(listOf(0L, 0L)))
    }

    @Test
    fun sweepAnglesTreatNegativeAmountsAsZero() {
        val sweeps = ChartGeometry.sweepAngles(listOf(-100L, 100L))

        assertEquals(listOf(0f, 360f), sweeps)
    }

    @Test
    fun startAnglesChainSweepsFromTwelveOClock() {
        val starts = ChartGeometry.startAngles(listOf(90f, 90f, 180f))

        assertEquals(listOf(-90f, 0f, 90f), starts)
    }

    @Test
    fun barFractionsScaleAgainstTheLargestValue() {
        val fractions = ChartGeometry.barFractions(listOf(0L, 50L, 100L))

        assertEquals(listOf(0f, 0.5f, 1f), fractions)
    }

    @Test
    fun barFractionsAreAllZeroWithoutData() {
        assertEquals(listOf(0f, 0f), ChartGeometry.barFractions(listOf(0L, 0L)))
    }

    @Test
    fun labelStrideKeepsVisibleLabelsUnderLimit() {
        assertEquals(1, ChartGeometry.labelStride(5, 6))
        assertEquals(5, ChartGeometry.labelStride(30, 6))
        assertEquals(1, ChartGeometry.labelStride(0, 6))
    }

    // ── 折线图几何（P7 资产趋势）──────────────────────────────────────

    @Test
    fun lineYRangeAlwaysIncludesZeroAndExpandsDegenerateSeries() {
        assertEquals(0L to 100L, ChartGeometry.lineYRange(listOf(listOf(20L, 100L))))
        assertEquals(-50L to 0L, ChartGeometry.lineYRange(listOf(listOf(-50L, -10L))))
        // 全零序列 → 扩展为 ±1，避免除零。
        assertEquals(-1L to 1L, ChartGeometry.lineYRange(listOf(listOf(0L, 0L))))
        assertEquals(0L to 1L, ChartGeometry.lineYRange(emptyList()))
    }

    @Test
    fun lineYRangeCoversAllSeriesWithSharedBounds() {
        val range = ChartGeometry.lineYRange(listOf(listOf(10L, 20L), listOf(-30L, 5L)))
        assertEquals(-30L to 20L, range)
    }

    @Test
    fun lineXSpansEvenlyAndCentersSinglePoint() {
        assertEquals(0f, ChartGeometry.lineX(3, 0))
        assertEquals(0.5f, ChartGeometry.lineX(3, 1))
        assertEquals(1f, ChartGeometry.lineX(3, 2))
        assertEquals(0.5f, ChartGeometry.lineX(1, 0), "单点居中")
        assertEquals(0.5f, ChartGeometry.lineX(0, 0))
    }

    @Test
    fun lineYFractionMapsBottomToTopAndClamps() {
        val yMin = -100L
        val yMax = 100L
        assertEquals(0f, ChartGeometry.lineYFraction(-100L, yMin, yMax))
        assertEquals(0.5f, ChartGeometry.lineYFraction(0L, yMin, yMax))
        assertEquals(1f, ChartGeometry.lineYFraction(100L, yMin, yMax))
        assertEquals(0f, ChartGeometry.lineYFraction(-500L, yMin, yMax), "值域外向下夹取")
        assertEquals(1f, ChartGeometry.lineYFraction(500L, yMin, yMax), "值域外向上夹取")
    }

    @Test
    fun zeroLineYFractionSitsInsideSharedRange() {
        assertEquals(0.5f, ChartGeometry.zeroLineYFraction(-100L, 100L))
        assertEquals(0f, ChartGeometry.zeroLineYFraction(0L, 100L))
        assertEquals(1f, ChartGeometry.zeroLineYFraction(-100L, 0L))
    }

    @Test
    fun guideIndexSnapsToNearestSample() {
        assertEquals(0, ChartGeometry.guideIndex(0.1f, 5))
        assertEquals(2, ChartGeometry.guideIndex(0.5f, 5))
        assertEquals(4, ChartGeometry.guideIndex(0.9f, 5))
        assertEquals(4, ChartGeometry.guideIndex(1.5f, 5), "越界夹取到末点")
        assertEquals(0, ChartGeometry.guideIndex(0.7f, 1))
        assertNull(ChartGeometry.guideIndex(0.5f, 0))
    }
}