package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme

/**
 * 居中加载态：与 [EmptyState] 对应的「正在读取」占位。
 *
 * - 不传 [message] 时只有进度圈（整屏加载）；
 * - 传了 [message] 就在圈下方补一行说明（如「正在同步」）。
 * 文案由调用方传入（strings.xml），组件内不写死用户可见文字。
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(VipLedgerTokens.IconHero),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "LoadingState - 带说明", showBackground = true)
@Composable
private fun LoadingStateWithMessagePreview() {
    VipLedgerTheme {
        LoadingState(message = "正在加载")
    }
}

@Preview(name = "LoadingState - 纯圈", showBackground = true)
@Composable
private fun LoadingStatePlainPreview() {
    VipLedgerTheme {
        LoadingState()
    }
}
