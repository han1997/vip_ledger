package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.han1997.vipledger.core.designsystem.R
import com.han1997.vipledger.core.designsystem.theme.serifTitle

/**
 * 统一顶栏：标题 + 可选返回 + 右侧动作。
 *
 * V3（墨绿金基准）：标题用衬线（原型 header h2：Title 23 serif，墨绿色）。
 * V2 调整：返回键补上朗读语义（common_back），装饰性图标保持 null。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipLedgerTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = stringResource(R.string.common_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        title = {
            Text(
                text = title,
                style = serifTitle,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription,
                    )
                }
            }
        },
        actions = actions,
    )
}
