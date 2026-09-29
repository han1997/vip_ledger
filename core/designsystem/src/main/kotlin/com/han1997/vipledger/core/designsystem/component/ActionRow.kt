package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme

/**
 * 可点击列表行（设置入口 / 管理页行 / 通用动作行）。
 *
 * - leading：可选图标槽（[leadingIconContentDescription] 为 null 时图标视为装饰，不朗读）。
 * - title + 可选 subtitle；trailing 可选文字值、开关（[checked]）或箭头。
 * - 有 [onClick] 时整行可点、带按压涟漪；无 onClick 时退化为纯展示行。
 */
@Composable
fun ActionRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary,
    leadingIconContentDescription: String? = null,
    trailingText: String? = null,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    trailingIconContentDescription: String? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .heightIn(min = VipLedgerTokens.TouchTargetMin)
    val rowModifier = if (onClick != null) {
        baseModifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        baseModifier
    }

    Row(
        modifier = rowModifier.padding(
            horizontal = VipLedgerTokens.Space4,
            vertical = VipLedgerTokens.Space3,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(VipLedgerTokens.IconHero)
                    .clip(CircleShape)
                    .background(leadingIconTint.copy(alpha = VipLedgerTokens.AlphaDecoration)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = leadingIconContentDescription,
                    tint = leadingIconTint,
                    modifier = Modifier.size(VipLedgerTokens.IconMedium),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = VipLedgerTokens.AlphaDisabled)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (checked != null) {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        } else if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = trailingIconContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(VipLedgerTokens.IconMedium),
            )
        }
    }
}

@Preview(name = "ActionRow - 完整形态", showBackground = true, widthDp = 360)
@Composable
private fun ActionRowFullPreview() {
    VipLedgerTheme {
        Column {
            ActionRow(
                title = "账本管理",
                subtitle = "3 本账本",
                onClick = {},
            )
            HorizontalDivider()
            ActionRow(
                title = "AI 自动记账",
                subtitle = "识别账单并生成草稿",
                checked = true,
                onCheckedChange = {},
            )
            HorizontalDivider()
            ActionRow(
                title = "备份到 WebDAV",
                trailingText = "昨天 20:14",
                onClick = {},
                leadingIcon = null,
            )
            HorizontalDivider()
            ActionRow(
                title = "统计",
                subtitle = "需要先创建账本",
                enabled = false,
                onClick = null,
            )
        }
    }
}
