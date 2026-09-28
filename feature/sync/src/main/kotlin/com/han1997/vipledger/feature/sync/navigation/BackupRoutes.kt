package com.han1997.vipledger.feature.sync.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.han1997.vipledger.feature.sync.ui.BackupScreen

/**
 * 备份与导入导出模块的导航路由。
 *
 * `:app` 只调用 [backupGraph] 与 [BackupRoutes.HOME]，不关心模块内部页面结构；
 * 与 `feature:stats` 的 `statsGraph()` 保持同一风格。备份是 `feature:sync` 的路由，
 * 不再挂在 `feature:ledger` 上。
 */
object BackupRoutes {
    const val HOME: String = "backup/home"
}

/**
 * 备份页没有自己的返回栈入口（`:app` 用无参的 `backupGraph()` 注册），
 * 返回动作交给系统的返回分发器，兼容预测性返回。
 */
fun NavGraphBuilder.backupGraph() {
    composable(BackupRoutes.HOME) {
        val backOwner = LocalOnBackPressedDispatcherOwner.current
        BackupScreen(onBack = { backOwner?.onBackPressedDispatcher?.onBackPressed() })
    }
}
