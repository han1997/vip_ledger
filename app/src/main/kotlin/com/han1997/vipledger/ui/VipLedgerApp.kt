package com.han1997.vipledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.han1997.vipledger.feature.ai.navigation.AiRoutes
import com.han1997.vipledger.feature.ai.navigation.aiGraph
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.navigation.ledgerGraph
import com.han1997.vipledger.feature.stats.navigation.StatsRoutes
import com.han1997.vipledger.feature.stats.navigation.statsGraph
import com.han1997.vipledger.feature.sync.navigation.BackupRoutes
import com.han1997.vipledger.feature.sync.navigation.backupGraph

/**
 * 应用的导航根。
 *
 * 各 feature 模块自带 graph（`ledgerGraph` / `statsGraph`），`:app` 只负责把它们拼起来：
 * 首页顶栏的「统计」入口、统计页里点账目回「编辑账目」，都靠这里的回调接线，
 * 两个 feature 之间不互相依赖。
 */
@Composable
fun VipLedgerApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = LedgerRoutes.HOME,
        modifier = modifier,
    ) {
        ledgerGraph(
            navController = navController,
            onOpenStats = { ledgerId -> navController.navigate(StatsRoutes.stats(ledgerId)) },
            onOpenBackup = { navController.navigate(BackupRoutes.HOME) },
            onOpenAi = { navController.navigate(AiRoutes.HOME) },
        )
        statsGraph(
            navController = navController,
            onEditTxn = { ledgerId, txnId -> navController.navigate(LedgerRoutes.txnEdit(ledgerId, txnId)) },
        )
        backupGraph()
        aiGraph()
    }
}
