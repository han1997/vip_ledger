package com.han1997.vipledger.feature.ledger.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.han1997.vipledger.feature.ledger.ui.drafts.DraftListScreen
import com.han1997.vipledger.feature.ledger.ui.home.HomeScreen
import com.han1997.vipledger.feature.ledger.ui.loan.LoanDetailScreen
import com.han1997.vipledger.feature.ledger.ui.loan.LoanEditScreen
import com.han1997.vipledger.feature.ledger.ui.loan.LoanListScreen
import com.han1997.vipledger.feature.ledger.ui.manage.AccountManageScreen
import com.han1997.vipledger.feature.ledger.ui.manage.CategoryManageScreen
import com.han1997.vipledger.feature.ledger.ui.manage.LedgerManageScreen
import com.han1997.vipledger.feature.ledger.ui.manage.TagManageScreen
import com.han1997.vipledger.feature.ledger.ui.recurring.RecurringEditScreen
import com.han1997.vipledger.feature.ledger.ui.recurring.RecurringListScreen
import com.han1997.vipledger.feature.ledger.ui.txnedit.TxnEditScreen
import com.han1997.vipledger.feature.ledger.ui.txnlist.TxnListScreen

/**
 * 记账主干的导航路由。`:app` 只需要调用 [ledgerGraph]，不关心内部页面结构；
 * 后续 `feature:stats` / `feature:sync` 各自提供自己的 graph。
 */
object LedgerRoutes {

    const val ARG_LEDGER_ID: String = "ledgerId"
    const val ARG_TXN_ID: String = "txnId"
    const val ARG_RULE_ID: String = "ruleId"
    const val ARG_PLAN_ID: String = "planId"

    const val HOME: String = "ledger/home"
    const val LEDGER_MANAGE: String = "ledger/manage/ledgers"

    const val TXN_LIST: String = "ledger/txns/{$ARG_LEDGER_ID}"
    const val TXN_EDIT: String = "ledger/txn/edit/{$ARG_LEDGER_ID}?$ARG_TXN_ID={$ARG_TXN_ID}"
    const val ACCOUNT_MANAGE: String = "ledger/manage/accounts/{$ARG_LEDGER_ID}"
    const val CATEGORY_MANAGE: String = "ledger/manage/categories/{$ARG_LEDGER_ID}"
    const val TAG_MANAGE: String = "ledger/manage/tags/{$ARG_LEDGER_ID}"

    const val RECURRING_LIST: String = "ledger/recurring/{$ARG_LEDGER_ID}"
    const val RECURRING_EDIT: String =
        "ledger/recurring/{$ARG_LEDGER_ID}/edit?$ARG_RULE_ID={$ARG_RULE_ID}"
    const val DRAFT_LIST: String = "ledger/drafts/{$ARG_LEDGER_ID}"
    const val LOAN_LIST: String = "ledger/loans/{$ARG_LEDGER_ID}"
    const val LOAN_DETAIL: String = "ledger/loans/{$ARG_LEDGER_ID}/detail/{$ARG_PLAN_ID}"
    const val LOAN_CREATE: String = "ledger/loans/{$ARG_LEDGER_ID}/create"

    fun txnList(ledgerId: String): String = "ledger/txns/$ledgerId"

    fun txnEdit(ledgerId: String, txnId: String? = null): String =
        "ledger/txn/edit/$ledgerId?$ARG_TXN_ID=" + txnId.orEmpty()

    fun accountManage(ledgerId: String): String = "ledger/manage/accounts/$ledgerId"

    fun categoryManage(ledgerId: String): String = "ledger/manage/categories/$ledgerId"

    fun tagManage(ledgerId: String): String = "ledger/manage/tags/$ledgerId"

    fun recurringList(ledgerId: String): String = "ledger/recurring/$ledgerId"

    fun recurringEdit(ledgerId: String, ruleId: String? = null): String =
        "ledger/recurring/$ledgerId/edit?$ARG_RULE_ID=" + ruleId.orEmpty()

    fun drafts(ledgerId: String): String = "ledger/drafts/$ledgerId"

    fun loanList(ledgerId: String): String = "ledger/loans/$ledgerId"

    fun loanDetail(ledgerId: String, planId: String): String = "ledger/loans/$ledgerId/detail/$planId"

    fun loanCreate(ledgerId: String): String = "ledger/loans/$ledgerId/create"
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
            onOpenRecurring = { ledgerId -> navController.navigate(LedgerRoutes.recurringList(ledgerId)) },
            onOpenDrafts = { ledgerId -> navController.navigate(LedgerRoutes.drafts(ledgerId)) },
            onOpenLoans = { ledgerId -> navController.navigate(LedgerRoutes.loanList(ledgerId)) },
            onOpenLoanDetail = { ledgerId, planId ->
                navController.navigate(LedgerRoutes.loanDetail(ledgerId, planId))
            },
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

    composable(
        route = LedgerRoutes.RECURRING_LIST,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) { entry ->
        val ledgerId = entry.arguments?.getString(LedgerRoutes.ARG_LEDGER_ID).orEmpty()
        RecurringListScreen(
            onBack = { navController.popBackStack() },
            onCreate = { navController.navigate(LedgerRoutes.recurringEdit(ledgerId)) },
            onEdit = { _, ruleId ->
                navController.navigate(LedgerRoutes.recurringEdit(ledgerId, ruleId))
            },
        )
    }

    composable(
        route = LedgerRoutes.RECURRING_EDIT,
        arguments = listOf(
            navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType },
            navArgument(LedgerRoutes.ARG_RULE_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        RecurringEditScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = LedgerRoutes.DRAFT_LIST,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) {
        DraftListScreen(
            onBack = { navController.popBackStack() },
            onOpenRecurring = { ledgerId -> navController.navigate(LedgerRoutes.recurringList(ledgerId)) },
        )
    }

    composable(
        route = LedgerRoutes.LOAN_LIST,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) { entry ->
        val ledgerId = entry.arguments?.getString(LedgerRoutes.ARG_LEDGER_ID).orEmpty()
        LoanListScreen(
            onBack = { navController.popBackStack() },
            onCreate = { navController.navigate(LedgerRoutes.loanCreate(ledgerId)) },
            onOpenDetail = { planId -> navController.navigate(LedgerRoutes.loanDetail(ledgerId, planId)) },
        )
    }

    composable(
        route = LedgerRoutes.LOAN_DETAIL,
        arguments = listOf(
            navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType },
            navArgument(LedgerRoutes.ARG_PLAN_ID) { type = NavType.StringType },
        ),
    ) {
        LoanDetailScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = LedgerRoutes.LOAN_CREATE,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) {
        LoanEditScreen(onBack = { navController.popBackStack() })
    }
}
