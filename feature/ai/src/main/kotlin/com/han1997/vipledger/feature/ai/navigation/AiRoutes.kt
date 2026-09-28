package com.han1997.vipledger.feature.ai.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.han1997.vipledger.feature.ai.ui.entry.AiEntryScreen

/**
 * AI 自然语言记账模块的导航路由。
 *
 * `:app` 只调用 [aiGraph] 与 [AiRoutes.HOME]，不关心模块内部结构；
 * 与 `feature:sync` 的 `backupGraph()` / `feature:stats` 的 `statsGraph()` 同一风格。
 * AI 页没有自己的返回栈入口（`:app` 用无参的 `aiGraph()` 注册），返回交给系统返回分发器。
 */
object AiRoutes {
    const val HOME: String = "ai/home"
}

fun NavGraphBuilder.aiGraph() {
    composable(AiRoutes.HOME) {
        val backOwner = LocalOnBackPressedDispatcherOwner.current
        AiEntryScreen(onBack = { backOwner?.onBackPressedDispatcher?.onBackPressed() })
    }
}
