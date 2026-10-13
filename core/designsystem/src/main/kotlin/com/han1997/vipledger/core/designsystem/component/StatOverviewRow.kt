package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.displayAmount

/** 三段概览里的一格。 */
data class OverviewStat(val label: String, val value: String, val valueColor: androidx.compose.ui.graphics.Color)

/**
 * 三段概览卡（原型 .overview）：支出 / 收入 / 结余并排，竖线分隔，数值用衬线。
 */
@Composable
fun StatOverviewRow(
    stats: List<OverviewStat>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusCard),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = VipLedgerTokens.Space4)
                            .width(VipLedgerTokens.BorderThin)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = VipLedgerTokens.Space2, vertical = 15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = stat.value,
                        style = displayAmount,
                        color = stat.valueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 排行行(原型 .rank .r):名称 + 占比 + 衬线金额 + 彩色进度条。
 */
@Composable
fun RankBarRow(
    name: String,
    percent: String,
    amount: String,
    barFraction: Float,
    barColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = percent,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = amount,
                style = displayAmount,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(VipLedgerTokens.RadiusFull)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor, RoundedCornerShape(VipLedgerTokens.RadiusFull)),
            )
        }
    }
}

@Preview(name = "StatOverviewRow", showBackground = true)
@Composable
private fun StatOverviewPreview() {
    VipLedgerTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatOverviewRow(
                stats = listOf(
                    OverviewStat("支出", "¥6,842.50", com.han1997.vipledger.core.designsystem.theme.ExpenseRed),
                    OverviewStat("收入", "¥12,000.00", com.han1997.vipledger.core.designsystem.theme.IncomeGreen),
                    OverviewStat("结余", "¥5,157.50", MaterialTheme.colorScheme.onSurface),
                ),
            )
        }
    }
}