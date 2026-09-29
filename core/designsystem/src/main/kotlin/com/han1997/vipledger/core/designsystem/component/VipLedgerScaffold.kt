package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Page chrome: warm background + tokenized insets.
 *
 * V2 约定：内容 insets 默认覆盖 systemBars + IME（键盘弹出时输入区不被遮挡）；
 * 各页面仍可通过 [contentWindowInsets] 覆盖（保持既有签名兼容）。
 */
@Composable
fun VipLedgerScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = VipLedgerScaffoldDefaults.contentWindowInsets(),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/** VipLedger 默认的 Scaffold 内容 insets：systemBars 与 IME 取并集。 */
object VipLedgerScaffoldDefaults {
    @Composable
    fun contentWindowInsets(): WindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
}
