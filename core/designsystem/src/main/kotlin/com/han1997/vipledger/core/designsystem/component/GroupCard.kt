package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme

/**
 * 圆角分组容器：设置页、管理页与首页的「一组行」共用。
 *
 * 统一了 surface 颜色、圆角（[VipLedgerTokens.RadiusMedium]）与 elevation；
 * 内部通过 [content] 放若干 [ActionRow] 或其他行组件。
 * 替代各页面重复的 `Card { ... }` 写法。
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = VipLedgerTokens.ElevationLevel1),
        shape = RoundedCornerShape(VipLedgerTokens.RadiusMedium),
        content = content,
    )
}

@Preview(name = "GroupCard", showBackground = true)
@Composable
private fun GroupCardPreview() {
    VipLedgerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GroupCard {
                ActionRow(title = "账本管理", onClick = {})
                HorizontalDivider()
                ActionRow(title = "账户管理", subtitle = "现金、储蓄卡等", onClick = {})
                HorizontalDivider()
                ActionRow(title = "分类管理", onClick = {}, enabled = false)
            }
        }
    }
}