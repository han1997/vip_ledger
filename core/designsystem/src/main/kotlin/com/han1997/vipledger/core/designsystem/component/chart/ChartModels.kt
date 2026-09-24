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