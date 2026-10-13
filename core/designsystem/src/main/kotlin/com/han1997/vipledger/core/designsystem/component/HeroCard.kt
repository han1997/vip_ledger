package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.BrassGold
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.displayHero
import com.han1997.vipledger.core.designsystem.theme.vipLedgerAccentColors
import com.han1997.vipledger.core.model.money.Money

/**
 * 首页 hero 容器：净资产卡的墨绿渐变底座。
 *
 * 原型 v2.1 质感：`linear-gradient(150deg, --accent-2, --accent-deep)` 对角渐变
 * + 1px 金色发丝描边（rgba(176,141,87,.28)）+ 圆角 16dp（`--radius`）。
 * 内容一律由调用方组装（金额用 [AmountText]），组件只负责容器视觉；
 * 文字必须显式取 `onHero`/`onHeroMuted`（象牙金白，≥ 4.5:1）。
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accents = vipLedgerAccentColors()
    val shape = RoundedCornerShape(VipLedgerTokens.RadiusCard)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(accents.heroGradientStart, accents.heroGradientEnd),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
                shape = shape,
            )
            .border(
                width = VipLedgerTokens.BorderThin,
                color = BrassGold.copy(alpha = 0.28f),
                shape = shape,
            )
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
