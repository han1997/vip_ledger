package com.han1997.vipledger.core.designsystem.component.chart

import kotlin.test.Test
import kotlin.test.assertEquals

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
}