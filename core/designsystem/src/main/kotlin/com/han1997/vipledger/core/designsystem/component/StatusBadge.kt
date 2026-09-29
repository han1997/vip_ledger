package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.IncomeGreen
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme

/** 状态徽标的视觉等级：决定用色强度。 */
enum class BadgeTone {
    /** 中性：普通信息（默认）。 */
    Neutral,

    /** 品牌：主色强调（如「本月」选中 chip）。 */
    Brand,

    /** 成功/收入：绿色系。 */
    Positive,

    /** 警示：琥珀/珊瑚系（如「待确认」「临近还款」）。 */
    Warning,

    /** 危险：红色系（如「逾期」「失败」）。 */
    Danger,
}

/**
 * 小型状态徽标 / 筛选 chip。
 *
 * - [StatusBadge]：静态标签（如「待确认」「已逾期」），圆角小胶囊；
 * - 颜色由 [tone] 决定，禁止调用方传入任意颜色（保证深浅色对比度统一）。
 * 文案由调用方传入（来自 strings.xml），组件内不写死用户可见文字。
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BadgeTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
        BadgeTone.Brand -> scheme.primaryContainer to scheme.onPrimaryContainer
        // 语义色底 + 8% 透明度容器：在深浅色下都保持语义红/绿可辨（charts.md 同思路）。
        BadgeTone.Positive -> IncomeGreen.copy(alpha = 0.14f) to IncomeGreen
        BadgeTone.Warning -> scheme.tertiary.copy(alpha = 0.14f) to scheme.tertiary
        BadgeTone.Danger -> ExpenseRed.copy(alpha = 0.14f) to ExpenseRed
    }
    Text(
        text = text,
        modifier = modifier
            .background(color = container, shape = RoundedCornerShape(VipLedgerTokens.RadiusFull))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(name = "StatusBadge - 五种语气", showBackground = true)
@Composable
private fun StatusBadgeTonesPreview() {
    VipLedgerTheme {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            StatusBadge(text = "草稿", tone = BadgeTone.Neutral)
            StatusBadge(text = "本月", tone = BadgeTone.Brand)
            StatusBadge(text = "已入账", tone = BadgeTone.Positive)
            StatusBadge(text = "3 天后到期", tone = BadgeTone.Warning)
            StatusBadge(text = "已逾期", tone = BadgeTone.Danger)
        }
    }
}
