package com.han1997.vipledger.feature.ledger.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.han1997.vipledger.feature.ledger.ui.home.HomeScreen
import com.han1997.vipledger.feature.ledger.ui.manage.AccountManageScreen
import com.han1997.vipledger.feature.ledger.ui.manage.CategoryManageScreen
import com.han1997.vipledger.feature.ledger.ui.manage.LedgerManageScreen
import com.han1997.vipledger.feature.ledger.ui.manage.TagManageScreen
import com.han1997.vipledger.feature.ledger.ui.txnedit.TxnEditScreen
import com.han1997.vipledger.feature.ledger.ui.txnlist.TxnListScreen

/**
 * 记账主干的导航路由。`:app` 只需要调用 [ledgerGraph]，不关心内部页面结构；
 * 后续 `feature:stats` / `feature:sync` 各自提供自己的 graph。
 */
object LedgerRoutes {

    const val ARG_LEDGER_ID: String = "ledgerId"
    const val ARG_TXN_ID: String = "txnId"

    const val HOME: String = "ledger/home"
    const val LEDGER_MANAGE: String = "ledger/manage/ledgers"

    const val TXN_LIST: String = "ledger/txns/{$ARG_LEDGER_ID}"
    const val TXN_EDIT: String = "ledger/txn/edit/{$ARG_LEDGER_ID}?$ARG_TXN_ID={$ARG_TXN_ID}"
    const val ACCOUNT_MANAGE: String = "ledger/manage/accounts/{$ARG_LEDGER_ID}"
    const val CATEGORY_MANAGE: String = "ledger/manage/categories/{$ARG_LEDGER_ID}"
    const val TAG_MANAGE: String = "ledger/manage/tags/{$ARG_LEDGER_ID}"

    fun txnList(ledgerId: String): String = "ledger/txns/$ledgerId"

    fun txnEdit(ledgerId: String, txnId: String? = null): String =
        "ledger/txn/edit/$ledgerId?$ARG_TXN_ID=" + txnId.orEmpty()

    fun accountManage(ledgerId: String): String = "ledger/manage/accounts/$ledgerId"

    fun categoryManage(ledgerId: String): String = "ledger/manage/categories/$ledgerId"

    fun tagManage(ledgerId: String): String = "ledger/manage/tags/$ledgerId"
}

fun NavGraphBuilder.ledgerGraph(
    navController: NavHostController,
    onOpenStats: (String) -> Unit,
) {
    composable(LedgerRoutes.HOME) {
        HomeScreen(
            onAddTxn = { ledgerId -> navController.navigate(LedgerRoutes.txnEdit(ledgerId)) },
            onOpenTxns = { ledgerId -> navController.navigate(LedgerRoutes.txnList(ledgerId)) },
            onOpenStats = onOpenStats,
            onOpenLedgers = { navController.navigate(LedgerRoutes.LEDGER_MANAGE) },
            onOpenAccounts = { ledgerId -> navController.navigate(LedgerRoutes.accountManage(ledgerId)) },
            onOpenCategories = { ledgerId -> navController.navigate(LedgerRoutes.categoryManage(ledgerId)) },
            onOpenTags = { ledgerId -> navController.navigate(LedgerRoutes.tagManage(ledgerId)) },
        )
    }

    composable(
        route = LedgerRoutes.TXN_LIST,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) { entry ->
        val ledgerId = entry.arguments?.getString(LedgerRoutes.ARG_LEDGER_ID).orEmpty()
        TxnListScreen(
            onBack = { navController.popBackStack() },
            onAddTxn = { navController.navigate(LedgerRoutes.txnEdit(ledgerId)) },
            onEditTxn = { txnId -> navController.navigate(LedgerRoutes.txnEdit(ledgerId, txnId)) },
        )
    }

    composable(
        route = LedgerRoutes.TXN_EDIT,
        arguments = listOf(
            navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType },
            navArgument(LedgerRoutes.ARG_TXN_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        TxnEditScreen(onBack = { navController.popBackStack() })
    }

    composable(LedgerRoutes.LEDGER_MANAGE) {
        LedgerManageScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = LedgerRoutes.ACCOUNT_MANAGE,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) {
        AccountManageScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = LedgerRoutes.CATEGORY_MANAGE,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) {
        CategoryManageScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = LedgerRoutes.TAG_MANAGE,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) {
        TagManageScreen(onBack = { navController.popBackStack() })
    }
}