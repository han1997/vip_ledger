package com.han1997.vipledger.core.designsystem.component.chart

import androidx.compose.ui.graphics.Color

/**
 * 环图的一段：名称、金额（分，非负）、颜色。
 *
 * 金额用「分」（Long）而不是 `Money`：`Money` 是 value class，
 * 不能出现在这些跨模块的公共数据类字段里做默认值比较（见 ui.md 的坑 2）。
 */
data class PieSlice(
    val label: String,
    val cents: Long,
    val color: Color,
)

/** 柱状图的一组：横轴标签 + 收入/支出（分，非负）。 */
data class BarGroup(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long,
)

/**
 * 折线图的一条序列（P7 资产趋势）：逐点金额（分，可负）+ 线条颜色。
 *
 * 颜色由调用方给出（`chartColorOf(index)` 或语义色），图表不做配色决策（charts.md）。
 * 同一张图的多条序列必须等长（同一批横轴采样点）。
 */
data class LineSeries(
    val points: List<Long>,
    val color: Color,
)