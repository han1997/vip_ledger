package com.han1997.vipledger.feature.stats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.feature.stats.ui.drill.DrillScreen
import com.han1997.vipledger.feature.stats.ui.search.SearchScreen
import com.han1997.vipledger.feature.stats.ui.stats.StatsScreen
import kotlinx.datetime.LocalDate

/**
 * 统计模块的导航路由。`:app` 只调用 [statsGraph]，不关心内部页面结构。
 *
 * 下钻的固定条件走查询参数（可空），与「明细」的路径参数风格保持一致。
 */
object StatsRoutes {

    const val ARG_LEDGER_ID: String = "ledgerId"
    const val ARG_CATEGORY_ID: String = "categoryId"
    const val ARG_TAG_ID: String = "tagId"
    const val ARG_ACCOUNT_ID: String = "accountId"
    const val ARG_PRESET: String = "preset"
    const val ARG_FROM: String = "from"
    const val ARG_TO: String = "to"

    const val STATS_TAB: String = "stats/tab"
    const val STATS: String = "stats/home/{$ARG_LEDGER_ID}"
    const val SEARCH: String = "stats/search/{$ARG_LEDGER_ID}"

    const val DRILL: String =
        "stats/drill/{$ARG_LEDGER_ID}" +
            "?$ARG_CATEGORY_ID={$ARG_CATEGORY_ID}" +
            "&$ARG_TAG_ID={$ARG_TAG_ID}" +
            "&$ARG_ACCOUNT_ID={$ARG_ACCOUNT_ID}" +
            "&$ARG_PRESET={$ARG_PRESET}" +
            "&$ARG_FROM={$ARG_FROM}" +
            "&$ARG_TO={$ARG_TO}"

    fun stats(ledgerId: String): String = "stats/home/$ledgerId"

    fun search(ledgerId: String): String = "stats/search/$ledgerId"

    /** 分类 / 标签 / 账户三选一 + 时间条件；三者为空表示「不按维度下钻」。 */
    fun drill(args: DrillArgs): String = "stats/drill/" + args.ledgerId +
        "?$ARG_CATEGORY_ID=" + args.categoryId.orEmpty() +
        "&$ARG_TAG_ID=" + args.tagId.orEmpty() +
        "&$ARG_ACCOUNT_ID=" + args.accountId.orEmpty() +
        "&$ARG_PRESET=" + (args.preset?.name ?: "") +
        "&$ARG_FROM=" + (args.range?.start?.toString() ?: "") +
        "&$ARG_TO=" + (args.range?.endInclusive?.toString() ?: "")
}

/** 下钻页的固定条件。 */
data class DrillArgs(
    val ledgerId: String,
    val categoryId: String? = null,
    val tagId: String? = null,
    val accountId: String? = null,
    val preset: DateRangePreset? = null,
    val range: DateRange? = null,
)

/**
 * 解析下钻路由参数。
 *
 * `SavedStateHandle.get<String>` 在缺参时返回的是空串而不是 null（见 ui.md 的坑 3），
 * 所以统一 `.takeIf { it.isNotBlank() }` 归一；非法枚举名/日期当作没传，不让路由参数把页面搞崩。
 */
fun drillArgsOf(
    ledgerId: String?,
    categoryId: String?,
    tagId: String?,
    accountId: String?,
    preset: String?,
    from: String?,
    to: String?,
): DrillArgs = DrillArgs(
    ledgerId = ledgerId.orEmpty(),
    categoryId = categoryId.takeIf { !it.isNullOrBlank() },
    tagId = tagId.takeIf { !it.isNullOrBlank() },
    accountId = accountId.takeIf { !it.isNullOrBlank() },
    preset = preset?.takeIf { it.isNotBlank() }
        ?.let { name -> DateRangePreset.entries.firstOrNull { it.name == name } },
    range = parseRange(from, to),
)

private fun parseRange(from: String?, to: String?): DateRange? {
    val start = from?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = to?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (start == null || end == null || start > end) return null
    return DateRange(start, end)
}

fun NavGraphBuilder.statsGraph(
    navController: NavHostController,
    onAddTxn: (ledgerId: String) -> Unit,
    onEditTxn: (ledgerId: String, txnId: String) -> Unit,
) {
    // 统计 Tab 根：无路径参数，ledgerId 由 StatsViewModel 观察当前账本得出（V3 底部导航）。
    composable(route = StatsRoutes.STATS_TAB) {
        StatsScreen(
            onBack = null,
            onAddTxn = onAddTxn,
            onOpenSearch = { ledgerId -> navController.navigate(StatsRoutes.search(ledgerId)) },
            onOpenDrill = { args -> navController.navigate(StatsRoutes.drill(args)) },
        )
    }

    composable(
        route = StatsRoutes.STATS,
        arguments = listOf(navArgument(StatsRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) {
        StatsScreen(
            onBack = { navController.popBackStack() },
            onAddTxn = onAddTxn,
            onOpenSearch = { ledgerId -> navController.navigate(StatsRoutes.search(ledgerId)) },
            onOpenDrill = { args -> navController.navigate(StatsRoutes.drill(args)) },
        )
    }

    composable(
        route = StatsRoutes.SEARCH,
        arguments = listOf(navArgument(StatsRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) {
        SearchScreen(
            onBack = { navController.popBackStack() },
            onEditTxn = onEditTxn,
        )
    }

    composable(
        route = StatsRoutes.DRILL,
        arguments = listOf(
            navArgument(StatsRoutes.ARG_LEDGER_ID) { type = NavType.StringType },
            navArgument(StatsRoutes.ARG_CATEGORY_ID) { type = NavType.StringType; defaultValue = "" },
            navArgument(StatsRoutes.ARG_TAG_ID) { type = NavType.StringType; defaultValue = "" },
            navArgument(StatsRoutes.ARG_ACCOUNT_ID) { type = NavType.StringType; defaultValue = "" },
            navArgument(StatsRoutes.ARG_PRESET) { type = NavType.StringType; defaultValue = "" },
            navArgument(StatsRoutes.ARG_FROM) { type = NavType.StringType; defaultValue = "" },
            navArgument(StatsRoutes.ARG_TO) { type = NavType.StringType; defaultValue = "" },
        ),
    ) {
        DrillScreen(
            onBack = { navController.popBackStack() },
            onEditTxn = onEditTxn,
        )
    }
}
