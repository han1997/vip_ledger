package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme

/**
 * 分组标题行：左侧标题 + 可选的 trailing 动作（如「查看全部」）。
 *
 * 替代各页面手写的「标题 + TextButton」行，保证字号、间距与点按区域一致。
 * 文案由调用方传入（来自 strings.xml），组件内不写死任何用户可见文字。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(text = actionLabel, maxLines = 1)
            }
        }
    }
}

@Preview(name = "SectionHeader - 带动作", showBackground = true)
@Composable
private fun SectionHeaderWithActionPreview() {
    VipLedgerTheme {
        SectionHeader(
            title = "最近账目",
            actionLabel = "查看全部",
            onActionClick = {},
        )
    }
}

@Preview(name = "SectionHeader - 仅标题", showBackground = true)
@Composable
private fun SectionHeaderPlainPreview() {
    VipLedgerTheme {
        SectionHeader(title = "基础信息")
    }
}
