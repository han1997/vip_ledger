package com.han1997.vipledger.core.designsystem.component.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.R
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.IncomeGreen

/**
 * 时间趋势柱状图（每个时间桶两根柱：收入 / 支出）。
 *
 * 柱高比例由 [ChartGeometry.barFractions] 算好，`Canvas` 只负责 `drawRoundRect`；
 * 横轴标签最多显示 [maxLabels] 个，其余留空避免文字叠在一起。
 */
@Composable
fun BarChart(
    groups: List<BarGroup>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    maxLabels: Int = 6,
    incomeColor: Color = IncomeGreen,
    expenseColor: Color = ExpenseRed,
    showLegend: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            if (groups.isEmpty()) return@Canvas
            val (baseline, fractions) = ChartGeometry.signedBars(
                groups.flatMap { listOf(it.incomeCents, it.expenseCents) },
            )
            val slot = size.width / groups.size
            val barWidth = (slot * BAR_WIDTH_RATIO).coerceAtLeast(1f)
            val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            val zeroY = baseline * size.height
            drawLine(expenseColor, Offset(0f, zeroY), Offset(size.width, zeroY))
            groups.forEachIndexed { index, _ ->
                val centerX = slot * index + slot / 2f
                val incomeHeight = fractions[index * 2] * size.height
                val expenseHeight = fractions[index * 2 + 1] * size.height
                drawRoundRect(
                    color = incomeColor,
                    topLeft = Offset(centerX - barWidth - BAR_GAP_PX, zeroY - incomeHeight.coerceAtLeast(0f)),
                    size = Size(barWidth, kotlin.math.abs(incomeHeight)),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = expenseColor,
                    topLeft = Offset(centerX + BAR_GAP_PX, zeroY - expenseHeight.coerceAtLeast(0f)),
                    size = Size(barWidth, kotlin.math.abs(expenseHeight)),
                    cornerRadius = radius,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stride = ChartGeometry.labelStride(groups.size, maxLabels)
            groups.forEachIndexed { index, group ->
                // 每个桶一个等宽格子保证标签与柱子对齐；格子很窄（30 天时约 36px），
                // 所以标签必须允许超出格子宽度，否则会被裁成「9」这样的单个字符。
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (index % stride == 0) {
                        Text(
                            text = group.label,
                            modifier = Modifier.wrapContentWidth(
                                align = Alignment.CenterHorizontally,
                                unbounded = true,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (showLegend) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendItem(color = incomeColor, label = stringResource(R.string.chart_income))
                LegendItem(color = expenseColor, label = stringResource(R.string.chart_expense))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val BAR_WIDTH_RATIO = 0.3f
private const val BAR_GAP_PX = 1f
