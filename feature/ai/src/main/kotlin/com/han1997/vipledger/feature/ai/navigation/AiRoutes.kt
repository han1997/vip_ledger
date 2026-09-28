package com.han1997.vipledger.feature.ai.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.han1997.vipledger.feature.ai.ui.entry.AiEntryScreen
import com.han1997.vipledger.feature.ai.ui.rules.MerchantRuleManageScreen

/**
 * AI 自然语言记账模块的导航路由。
 *
 * `:app` 只调用 [aiGraph] 与 [AiRoutes.HOME]，不关心模块内部结构；
 * 与 `feature:sync` 的 `backupGraph()` / `feature:stats` 的 `statsGraph()` 同一风格。
 * AI 首页没有自己的返回栈入口（返回交给系统返回分发器）；[AiRoutes.RULES] 是模块内的第二页，
 * 从首页进入、返回时弹栈，因此 [aiGraph] 接收 `:app` 的 `NavHostController` 以完成页内跳转。
 */
object AiRoutes {
    const val HOME: String = "ai/home"
    const val RULES: String = "ai/rules"
}

fun NavGraphBuilder.aiGraph(navController: NavHostController) {
    composable(AiRoutes.HOME) {
        val backOwner = LocalOnBackPressedDispatcherOwner.current
        AiEntryScreen(
            onBack = { backOwner?.onBackPressedDispatcher?.onBackPressed() },
            onOpenRules = { navController.navigate(AiRoutes.RULES) },
        )
    }

    composable(AiRoutes.RULES) {
        MerchantRuleManageScreen(onBack = { navController.popBackStack() })
    }
}
