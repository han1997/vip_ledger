package com.han1997.vipledger.core.designsystem.component.chart

import kotlin.test.*

class SignedBarTest {
    @Test fun mixedAndNegativeOnlyValuesKeepVisibleSignedExtents() {
        assertEquals(0.5f to listOf(0.5f, -0.5f), ChartGeometry.signedBars(listOf(100, -100)))
        assertEquals(0f to listOf(-1f), ChartGeometry.signedBars(listOf(-100)))
        assertEquals(1f to listOf(0f), ChartGeometry.signedBars(listOf(0)))
        val (zero, values) = ChartGeometry.signedBars(listOf(Long.MAX_VALUE, Long.MIN_VALUE))
        assertTrue(zero.isFinite()); assertTrue(values.all { it.isFinite() })
    }
}
