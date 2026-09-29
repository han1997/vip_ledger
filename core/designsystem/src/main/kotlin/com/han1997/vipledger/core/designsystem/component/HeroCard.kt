package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.displayHero
import com.han1997.vipledger.core.designsystem.theme.vipLedgerAccentColors
import com.han1997.vipledger.core.model.money.Money

/**
 * 首页 hero 容器：净资产卡的轻量渐变底座。
 *
 * - 浅色模式：品牌绿 → 亮绿的横向轻渐变（「活泼品牌化」的有限装饰）；
 * - 深色模式：[VipLedgerAccentColors.heroGradientStart]/[heroGradientEnd] 两端同色，
 *   自动退化为纯色容器，保证大额金额的可读性。
 * 内容一律由调用方组装（金额用 [AmountText]），组件只负责容器视觉。
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accents = vipLedgerAccentColors()
    val shape = RoundedCornerShape(VipLedgerTokens.RadiusMedium)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(accents.heroGradientStart, accents.heroGradientEnd),
                ),
                shape = shape,
            ),
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.bodyLarge) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(VipLedgerTokens.Space5),
                content = content,
            )
        }
    }
}

@Preview(name = "HeroCard", showBackground = true)
@Composable
private fun HeroCardPreview() {
    VipLedgerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            HeroCard {
                Text(
                    text = "净资产",
                    style = MaterialTheme.typography.labelMedium,
                )
                AmountText(
                    amount = Money.ofCents(123_456_700L),
                    style = displayHero,
                )
            }
        }
    }
}
