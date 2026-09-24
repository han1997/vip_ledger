package com.han1997.vipledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.navigation.ledgerGraph

/**
 * 应用的导航根。
 *
 * 各 feature 模块自带 graph（`ledgerGraph` / 后续的 stats、sync），`:app` 只负责把它们拼起来，
 * 不关心页面内部结构，也不做业务逻辑。
 */
@Composable
fun VipLedgerApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = LedgerRoutes.HOME,
        modifier = modifier,
    ) {
        ledgerGraph(navController)
    }
}