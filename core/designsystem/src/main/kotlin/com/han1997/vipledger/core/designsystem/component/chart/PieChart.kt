package com.han1997.vipledger.core.designsystem.component.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.R
import java.util.Locale

/**
 * 占比环图 + 图例（分类/标签/账户占比都用它）。
 *
 * 纯 `Canvas` 自绘，不引第三方图表库；弧度计算在 [ChartGeometry] 里，可单测。
 * 没有数据时画一圈灰色空环，不留白。
 */
@Composable
fun PieChart(
    slices: List<PieSlice>,
    valueText: (PieSlice) -> String,
    modifier: Modifier = Modifier,
    chartSize: Dp = 168.dp,
    strokeWidth: Dp = 24.dp,
    centerTitle: String? = null,
    centerCaption: String? = null,
) {
    val visible = slices.filter { it.cents > 0L }
    val sweeps = ChartGeometry.sweepAngles(visible.map { it.cents })
    val starts = ChartGeometry.startAngles(sweeps)
    val total = visible.sumOf { it.cents }
    val emptyRingColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(chartSize),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                if (visible.isEmpty()) {
                    drawArc(
                        color = emptyRingColor,
                        startAngle = 0f,
                        sweepAngle = ChartGeometry.FULL_CIRCLE,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                } else {
                    visible.forEachIndexed { index, slice ->
                        drawArc(
                            color = slice.color,
                            startAngle = starts[index],
                            sweepAngle = sweeps[index],
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (centerTitle != null) {
                    Text(
                        text = centerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                if (centerCaption != null) {
                    Text(
                        text = centerCaption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        visible.forEach { slice ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color = slice.color, shape = CircleShape),
                )
                Text(
                    text = slice.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = percentLabel(ChartGeometry.fractionOf(slice.cents, total)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = valueText(slice),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.chart_empty),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 固定用 [Locale.US] 格式化百分比，避免不同设备语言下小数点/百分号位置漂移。 */
fun percentLabel(fraction: Float): String = String.format(Locale.US, "%.1f%%", fraction * 100f)