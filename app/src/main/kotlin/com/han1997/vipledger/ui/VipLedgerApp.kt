package com.han1997.vipledger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.han1997.vipledger.core.designsystem.R as DesignSystemR
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
 * `:app` 负责底部导航栏：首页 / 日历 / 统计 / 我的 四个 Tab 各自独立返回栈，
 * 互不覆盖；次级页（交易列表、编辑、管理页、备份、AI 配置）在 Tab 之上叠导航。
 * 各 feature 模块自带 graph，`:app` 只负责把它们拼起来。
 */
@Composable
fun VipLedgerApp(modifier: Modifier = Modifier, openAutoBook: Boolean = false, openBudgetLedger: String? = null) {
    val navController = rememberNavController()
    androidx.compose.runtime.LaunchedEffect(openBudgetLedger) {
        if (!openBudgetLedger.isNullOrBlank()) navController.navigate(LedgerRoutes.budget(openBudgetLedger)) { launchSingleTop = true }
    }
    androidx.compose.runtime.LaunchedEffect(openAutoBook) {
        if (openAutoBook) navController.navigate(LedgerRoutes.AUTO_BOOK) { launchSingleTop = true }
    }
    val backStackEntries by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntries?.destination?.route

    // 只在四个 Tab 根上显示底部栏；次级页进入后隐藏。
    val showBottomBar = when (currentRoute) {
        LedgerRoutes.HOME, LedgerRoutes.CALENDAR, LedgerRoutes.SETTINGS_TAB, StatsRoutes.STATS_TAB -> true
        else -> false
    }

    VipLedgerScaffoldWithBottomBar(
        showBottomBar = showBottomBar,
        currentRoute = currentRoute,
        onSelectTab = { route -> navigateTab(navController, route) },
    ) {
        NavHost(
            navController = navController,
            startDestination = LedgerRoutes.HOME,
            modifier = modifier,
        ) {
            ledgerGraph(
                navController = navController,
                onOpenBackup = { navController.navigate(BackupRoutes.HOME) },
                onOpenAiModel = { navController.navigate(AiRoutes.MODEL) },
                onOpenAiRules = { navController.navigate(AiRoutes.RULES) },
                onOpenSearch = { navController.navigate(StatsRoutes.search(it)) },
                onOpenRange = { ledger, range -> navController.navigate(StatsRoutes.drill(com.han1997.vipledger.feature.stats.navigation.DrillArgs(ledgerId = ledger, range = range))) },
            )
            statsGraph(
                navController = navController,
                onAddTxn = { ledgerId -> navController.navigate(LedgerRoutes.txnEdit(ledgerId)) },
                onEditTxn = { ledgerId, txnId -> navController.navigate(LedgerRoutes.txnEdit(ledgerId, txnId)) },
            )
            backupGraph()
            aiGraph(navController)
        }
    }
}

/** Tab 间切换：popUpTo 图起点重建目标栈，保留各自状态（saveState/restoreState）。 */
private fun navigateTab(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 底部导航栏容器：四个 Tab 各自挂着标题与图标；
 * 次级页（showBottomBar = false）时整栏隐藏，内容直接铺满。
 */
@Composable
private fun VipLedgerScaffoldWithBottomBar(
    showBottomBar: Boolean,
    currentRoute: String?,
    onSelectTab: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    // material3 的 Scaffold 已在 NavHost 外层由各页面自管；这里只做条件性的底栏包裹。
    if (!showBottomBar) {
        content()
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) { content() }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            TabDestinations.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
                    onClick = { onSelectTab(tab.route) },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == tab.route) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(tab.labelRes),
                        )
                    },
                    label = { Text(text = stringResource(tab.labelRes)) },
                )
            }
        }
    }
}

private data class TabDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TabDestinations: List<TabDestination> = listOf(
    TabDestination(
        route = LedgerRoutes.HOME,
        labelRes = DesignSystemR.string.tab_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    TabDestination(
        route = LedgerRoutes.CALENDAR,
        labelRes = DesignSystemR.string.tab_calendar,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
    ),
    TabDestination(
        route = StatsRoutes.STATS_TAB,
        labelRes = DesignSystemR.string.tab_stats,
        selectedIcon = Icons.Filled.PieChart,
        unselectedIcon = Icons.Outlined.PieChart,
    ),
    TabDestination(
        route = LedgerRoutes.SETTINGS_TAB,
        labelRes = DesignSystemR.string.tab_mine,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)
