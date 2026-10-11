package com.han1997.vipledger.feature.ai.navigation

import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.han1997.vipledger.feature.ai.ui.entry.AiEntryScreen
import com.han1997.vipledger.feature.ai.ui.model.ModelManageScreen
import com.han1997.vipledger.feature.ai.ui.rules.MerchantRuleManageScreen

/**
 * AI 自然语言记账模块的导航路由。
 *
 * `:app` 只调用 [aiGraph] 与 [AiRoutes.HOME]，不关心模块内部结构；
 * 与 `feature:sync` 的 `backupGraph()` / `feature:stats` 的 `statsGraph()` 同一风格。
 * AI 首页没有自己的返回栈入口（返回交给系统返回分发器）；[AiRoutes.RULES]（商户规则）与
 * [AiRoutes.MODEL]（端侧模型）是模块内的次级页，从首页进入、返回时弹栈，因此 [aiGraph]
 * 接收 `:app` 的 `NavHostController` 以完成页内跳转。
 *
 * [HOME] 携带可选参数 `shared`：系统分享（`ACTION_SEND`，image 类型）带入的图片 Uri 列表，
 * 由 [sharedImages] 编码（换行分隔），AI 页 ViewModel 从 SavedStateHandle 一次性消费。
 */
object AiRoutes {
    const val HOME: String = "ai/home?shared={shared}"
    const val RULES: String = "ai/rules"
    const val MODEL: String = "ai/model"

    /** 系统分享入口：把图片 Uri 列表编码进 [HOME]（换行分隔，Uri.encode 保证导航参数安全）。 */
    fun sharedImages(uris: List<String>): String =
        "ai/home?shared=" + Uri.encode(uris.joinToString("\n"))
}

fun NavGraphBuilder.aiGraph(navController: NavHostController) {
    composable(
        route = AiRoutes.HOME,
        arguments = listOf(navArgument("shared") { type = NavType.StringType; defaultValue = "" }),
    ) {
        val backOwner = LocalOnBackPressedDispatcherOwner.current
        AiEntryScreen(
            onBack = { backOwner?.onBackPressedDispatcher?.onBackPressed() },
            onOpenRules = { navController.navigate(AiRoutes.RULES) },
            onOpenModel = { navController.navigate(AiRoutes.MODEL) },
        )
    }

    composable(AiRoutes.RULES) {
        MerchantRuleManageScreen(onBack = { navController.popBackStack() })
    }

    composable(AiRoutes.MODEL) {
        ModelManageScreen(onBack = { navController.popBackStack() })
    }
}
