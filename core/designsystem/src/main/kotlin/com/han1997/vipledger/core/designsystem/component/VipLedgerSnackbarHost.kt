package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme

/**
 * 统一 Snackbar 容器：圆角、浮起、正/负反馈同一种视觉语言。
 *
 * - [VipLedgerSnackbarHost] 放进 `VipLedgerScaffold(snackbarHost = { ... })`；
 * - 反馈语义由 [VipLedgerSnackbarStyle] 决定：错误用语义红底白字，成功/信息用反色底。
 * 文案由调用方经 SnackbarHostState 的消息传入（strings.xml），组件内不写死文字。
 */
enum class VipLedgerSnackbarStyle { Info, Success, Error }

@Composable
fun VipLedgerSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    style: VipLedgerSnackbarStyle = VipLedgerSnackbarStyle.Info,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        VipLedgerSnackbar(data = data, style = style)
    }
}

@Composable
private fun VipLedgerSnackbar(data: SnackbarData, style: VipLedgerSnackbarStyle) {
    val scheme = MaterialTheme.colorScheme
    val (container, contentColor) = when (style) {
        VipLedgerSnackbarStyle.Info -> scheme.inverseSurface to scheme.inverseOnSurface
        VipLedgerSnackbarStyle.Success -> scheme.primary to scheme.onPrimary
        VipLedgerSnackbarStyle.Error -> ExpenseRed to Color.White
    }
    Snackbar(
        snackbarData = data,
        containerColor = container,
        contentColor = contentColor,
        actionContentColor = contentColor,
        dismissActionContentColor = contentColor,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusMedium),
    )
}

@Preview(name = "Snackbar", showBackground = true)
@Composable
private fun VipLedgerSnackbarHostPreview() {
    VipLedgerTheme {
        VipLedgerSnackbarHost(
            hostState = SnackbarHostState(),
            modifier = Modifier.padding(16.dp),
        )
    }
}
