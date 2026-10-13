package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.serifTitle

/**
 * 分组标题行:左侧衬线墨绿标题 + 可选的 trailing 动作(如「查看统计」)。
 *
 * 原型 v2.1:`.section-head h3` 为 16 serif 墨绿;动作为淡灰小字。
 * 文案由调用方传入(来自 strings.xml),组件内不写死任何用户可见文字。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VipLedgerTokens.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = serifTitle,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(vertical = VipLedgerTokens.Space1),
            )
        }
    }
}

@Preview(name = "SectionHeader - 带动作", showBackground = true)
@Composable
private fun SectionHeaderWithActionPreview() {
    VipLedgerTheme {
        SectionHeader(
            title = "最近流水",
            actionLabel = "查看统计",
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