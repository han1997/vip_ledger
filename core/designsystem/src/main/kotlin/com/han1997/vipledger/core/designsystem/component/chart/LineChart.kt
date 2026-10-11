package com.han1997.vipledger.core.designsystem.component.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens

/**
 * 折线图（P7 资产趋势）：多条等长序列 + 零线参考 + 可选选中列虚线。
 *
 * 几何全部来自 [ChartGeometry] 的纯函数（charts.md：算术与绘制分离）；
 * 颜色由调用方经 [LineSeries.color] 传入，图表不做配色决策。
 * 金额单位为分（Long），显示文案由界面层用 `Money` 组装。
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    /** 选中采样点下标（null 不画引导线）；由页面手势决定，图表只负责画。 */
    selectedIndex: Int? = null,
) {
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pointCount = series.firstOrNull()?.points?.size ?: 0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (pointCount == 0 || series.isEmpty()) return@Canvas
        val yMinMax = ChartGeometry.lineYRange(series.map { it.points })
        val zeroY = size.height * (1f - ChartGeometry.zeroLineYFraction(yMinMax.first, yMinMax.second))

        // 零线参考（净值可为负，零线是读图锚点）。
        if (ChartGeometry.zeroLineYFraction(yMinMax.first, yMinMax.second) > 0f) {
            drawLine(
                color = zeroLineColor,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = VipLedgerTokens.BorderThin.toPx(),
            )
        }

        val strokeWidth = VipLedgerTokens.BorderMedium.toPx() * 2f
        series.forEach { line ->
            val path = Path()
            line.points.forEachIndexed { index, value ->
                val x = size.width * ChartGeometry.lineX(pointCount, index)
                val y = size.height * (1f - ChartGeometry.lineYFraction(value, yMinMax.first, yMinMax.second))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = line.color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }

        // 选中列：竖直引导线 + 各序列取点圆点。
        selectedIndex?.takeIf { it in 0 until pointCount }?.let { index ->
            val guideX = size.width * ChartGeometry.lineX(pointCount, index)
            drawLine(
                color = guideColor.copy(alpha = VipLedgerTokens.AlphaSubtle),
                start = Offset(guideX, 0f),
                end = Offset(guideX, size.height),
                strokeWidth = VipLedgerTokens.BorderThin.toPx(),
            )
            series.forEach { line ->
                val y = size.height * (1f - ChartGeometry.lineYFraction(line.points[index], yMinMax.first, yMinMax.second))
                drawCircle(color = line.color, radius = strokeWidth, center = Offset(guideX, y))
            }
        }
    }
}