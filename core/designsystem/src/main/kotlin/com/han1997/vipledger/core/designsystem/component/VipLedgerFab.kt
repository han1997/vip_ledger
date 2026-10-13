package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.BrassGoldLight
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.vipLedgerAccentColors

/**
 * 记一笔悬浮按钮（原型 v2.1 `.fab`）。
 *
 * 56×56 方圆角（19px）+ 墨绿对角渐变 + 金色发丝描边 + 深投影，仅图标（象牙金白）。
 * 与原型一致：icon-only；朗读语义由 [contentDescription] 提供。
 * 触摸目标 56dp ≥ 48dp 无障碍下限。
 */
@Composable
fun VipLedgerFab(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
) {
    val accents = vipLedgerAccentColors()
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(elevation = 14.dp, shape = shape)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(accents.heroGradientStart, accents.heroGradientEnd),
                ),
                shape = shape,
            )
            .border(
                width = VipLedgerTokens.BorderThin,
                color = BrassGoldLight.copy(alpha = 0.35f),
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accents.onHero,
            modifier = Modifier.size(25.dp),
        )
    }
}
