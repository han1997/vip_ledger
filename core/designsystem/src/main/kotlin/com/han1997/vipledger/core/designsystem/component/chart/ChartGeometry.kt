package com.han1997.vipledger.core.designsystem.component.chart

import kotlin.math.ceil

/**
 * 图表的纯几何计算。
 *
 * 抽出来是为了能直接单测：Compose 的 `Canvas` 只能靠手工走查，
 * 但「占比 → 弧度」「金额 → 柱高比例」这类算术必须可验证。
 */
object ChartGeometry {
    /** Zero baseline and signed extents in [0, 1], calculated in Double to avoid Long overflow. */
    fun signedBars(values: List<Long>): Pair<Float, List<Float>> {
        val positive = values.maxOrNull()?.coerceAtLeast(0)?.toDouble() ?: 0.0
        val negative = -(values.minOrNull()?.coerceAtMost(0)?.toDouble() ?: 0.0)
        val span = positive + negative
        if (span == 0.0) return 1f to values.map { 0f }
        return (positive / span).toFloat() to values.map { (it.toDouble() / span).toFloat() }
    }

    /** Compose 的角度以 3 点方向为 0°、顺时针为正；环图从 12 点方向开始画。 */
    const val START_ANGLE: Float = -90f

    const val FULL_CIRCLE: Float = 360f

    /** 每段的弧度；总值为 0 时全部返回 0（界面画一圈灰环表示无数据）。 */
    fun sweepAngles(values: List<Long>): List<Float> {
        val total = values.sumOf { it.coerceAtLeast(0L) }
        if (total <= 0L) return List(values.size) { 0f }
        return values.map { fractionOf(it, total) * FULL_CIRCLE }
    }

    /** 每段的起始角（与 [sweepAngles] 一一对应、首尾相接）。 */
    fun startAngles(sweeps: List<Float>, startAngle: Float = START_ANGLE): List<Float> {
        var cursor = startAngle
        return sweeps.map { sweep ->
            val current = cursor
            cursor += sweep
            current
        }
    }

    /** 金额占总量的比例（0..1）；[total] 非正或 [value] 为负时返回 0。 */
    fun fractionOf(value: Long, total: Long): Float {
        if (total <= 0L) return 0f
        return (value.coerceAtLeast(0L).toDouble() / total.toDouble()).toFloat()
    }

    /** 相对最大值的柱高比例（0..1）；全为 0 时返回全 0。 */
    fun barFractions(values: List<Long>): List<Float> {
        val max = values.maxOrNull()?.coerceAtLeast(0L) ?: 0L
        return values.map { fractionOf(it, max) }
    }

    /**
     * 横轴标签的取样步长：最多显示 [maxLabels] 个标签，避免文字重叠。
     * 返回 1 表示每个都显示。
     */
    fun labelStride(count: Int, maxLabels: Int): Int {
        if (count <= 0 || maxLabels <= 0) return 1
        return ceil(count.toDouble() / maxLabels.toDouble()).toInt().coerceAtLeast(1)
    }
}
