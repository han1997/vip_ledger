package com.han1997.vipledger.feature.ledger.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
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
import com.han1997.vipledger.feature.ledger.ui.settings.SettingsScreen
import com.han1997.vipledger.feature.ledger.ui.settings.SettingsTabViewModel
import com.han1997.vipledger.feature.ledger.ui.settings.rememberInstalledAppVersion
import com.han1997.vipledger.feature.ledger.ui.txnedit.TxnEditScreen
import com.han1997.vipledger.feature.ledger.ui.txnlist.TxnListScreen

/**
 * 记账主干的导航路由。`:app` 只需要调用 [ledgerGraph]，不关心内部页面结构；
 * 后续 `feature:stats` / `feature:sync` 各自提供自己的 graph。
 */
object LedgerRoutes {
    const val ASSETS = "ledger/assets"
    const val ACCOUNT_DETAIL = "ledger/account/{ledgerId}/{accountId}"
    const val SAVINGS = "ledger/savings/{ledgerId}"
    const val HOME_LAYOUT = "ledger/home-layout/{ledgerId}"
    fun accountDetail(ledgerId: String, accountId: String) = "ledger/account/$ledgerId/$accountId"
    fun savings(ledgerId: String) = "ledger/savings/$ledgerId"
    fun homeLayout(ledgerId: String) = "ledger/home-layout/$ledgerId"
    const val CALENDAR: String = "ledger/calendar"
    const val BUDGET: String = "ledger/budget/{ledgerId}"
    const val ARG_DATE: String = "date"
    fun budget(ledgerId: String) = "ledger/budget/$ledgerId"
    const val AUTO_BOOK = "ledger/auto-book"

    const val ARG_LEDGER_ID: String = "ledgerId"
    const val ARG_TXN_ID: String = "txnId"
    const val ARG_RULE_ID: String = "ruleId"
    const val ARG_PLAN_ID: String = "planId"

    const val HOME: String = "ledger/home"
    const val SETTINGS_TAB: String = "ledger/settings-tab"
    const val SETTINGS: String = "ledger/settings/{$ARG_LEDGER_ID}"
    const val LEDGER_MANAGE: String = "ledger/manage/ledgers"

    const val TXN_LIST: String = "ledger/txns/{$ARG_LEDGER_ID}"
    const val TXN_EDIT: String = "ledger/txn/edit/{$ARG_LEDGER_ID}?$ARG_TXN_ID={$ARG_TXN_ID}&$ARG_DATE={$ARG_DATE}&accountId={accountId}&entryType={entryType}"
    const val ACCOUNT_MANAGE: String = "ledger/manage/accounts/{$ARG_LEDGER_ID}?editAccountId={editAccountId}"
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

    fun txnEdit(ledgerId: String, txnId: String? = null, date: kotlinx.datetime.LocalDate? = null, accountId: String? = null): String =
        "ledger/txn/edit/$ledgerId?$ARG_TXN_ID=" + txnId.orEmpty() + "&$ARG_DATE=" + (date?.toString() ?: "") + (accountId?.let { "&accountId=$it&entryType=TRANSFER" } ?: "")

    fun accountManage(ledgerId: String, editAccountId: String? = null): String = "ledger/manage/accounts/$ledgerId" + (editAccountId?.let { "?editAccountId=$it" } ?: "")

    fun categoryManage(ledgerId: String): String = "ledger/manage/categories/$ledgerId"

    fun tagManage(ledgerId: String): String = "ledger/manage/tags/$ledgerId"

    fun recurringList(ledgerId: String): String = "ledger/recurring/$ledgerId"

    fun recurringEdit(ledgerId: String, ruleId: String? = null): String =
        "ledger/recurring/$ledgerId/edit?$ARG_RULE_ID=" + ruleId.orEmpty()

    fun drafts(ledgerId: String): String = "ledger/drafts/$ledgerId"

    fun loanList(ledgerId: String): String = "ledger/loans/$ledgerId"

    fun loanDetail(ledgerId: String, planId: String): String = "ledger/loans/$ledgerId/detail/$planId"

    fun loanCreate(ledgerId: String): String = "ledger/loans/$ledgerId/create"

    fun settings(ledgerId: String): String = "ledger/settings/$ledgerId"
}

fun NavGraphBuilder.ledgerGraph(
    navController: NavHostController,
    onOpenBackup: () -> Unit,
    onOpenAiModel: () -> Unit,
    onOpenAiRules: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenRange: (String, com.han1997.vipledger.core.model.time.DateRange) -> Unit,
    onOpenAssets: () -> Unit,
) {
    composable(LedgerRoutes.ASSETS) {
        com.han1997.vipledger.feature.ledger.ui.wealth.AssetsScreen(
            onAccount = { ledger, account -> navController.navigate(LedgerRoutes.accountDetail(ledger, account)) },
            onManage = { navController.navigate(LedgerRoutes.accountManage(it)) },
            onSavings = { navController.navigate(LedgerRoutes.savings(it)) },
            onLedgers = { navController.navigate(LedgerRoutes.LEDGER_MANAGE) },
        )
    }
    composable(LedgerRoutes.ACCOUNT_DETAIL, arguments = listOf(navArgument("ledgerId") { type = NavType.StringType }, navArgument("accountId") { type = NavType.StringType })) {
        com.han1997.vipledger.feature.ledger.ui.wealth.AccountDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { ledger, account -> navController.navigate(LedgerRoutes.accountManage(ledger, account)) },
            onTransfer = { ledger, account -> navController.navigate(LedgerRoutes.txnEdit(ledger, accountId = account)) },
            onEditTxn = { ledger, txn -> navController.navigate(LedgerRoutes.txnEdit(ledger, txn)) },
        )
    }
    composable(LedgerRoutes.SAVINGS, arguments = listOf(navArgument("ledgerId") { type = NavType.StringType })) { entry ->
        com.han1997.vipledger.feature.ledger.ui.wealth.SavingsScreen(
            onBack = { navController.popBackStack() },
            onManage = { navController.navigate(LedgerRoutes.accountManage(entry.arguments?.getString("ledgerId").orEmpty())) },
            onEditTxn = { ledger, txn -> navController.navigate(LedgerRoutes.txnEdit(ledger, txn)) },
        )
    }
    composable(LedgerRoutes.HOME_LAYOUT, arguments = listOf(navArgument("ledgerId") { type = NavType.StringType })) {
        com.han1997.vipledger.feature.ledger.ui.wealth.HomeLayoutScreen(onBack = { navController.popBackStack() })
    }
    composable(LedgerRoutes.CALENDAR) {
        com.han1997.vipledger.feature.ledger.ui.calendar.CalendarScreen(
            onAdd = { ledger, date -> navController.navigate(LedgerRoutes.txnEdit(ledger, date = date)) },
            onEdit = { ledger, txn -> navController.navigate(LedgerRoutes.txnEdit(ledger, txn)) },
            onOpenLedgers = { navController.navigate(LedgerRoutes.LEDGER_MANAGE) },
        )
    }
    composable(LedgerRoutes.BUDGET, arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType })) {
        com.han1997.vipledger.feature.ledger.ui.budget.BudgetScreen(onBack = { navController.popBackStack() })
    }
    composable(LedgerRoutes.AUTO_BOOK) {
        com.han1997.vipledger.feature.ledger.ui.autobook.AutoBookScreen(
            onBack = { navController.popBackStack() },
            onEditTxn = { ledger, txn -> navController.navigate(LedgerRoutes.txnEdit(ledger, txn)) },
        )
    }
    // 「我的」Tab 根：SettingsScreen 无返回箭头，badge 由 navController 上层传入。
    composable(LedgerRoutes.SETTINGS_TAB) {
        val viewModel: SettingsTabViewModel = hiltViewModel()
        val currentLedger by viewModel.currentLedger.collectAsStateWithLifecycle()
        val pendingDraftCount by viewModel.pendingDraftCount.collectAsStateWithLifecycle()
        val ledgerId = currentLedger?.id.orEmpty()
        val ready = ledgerId.isNotBlank()
        SettingsScreen(
            ledgerReady = ready,
            appVersion = rememberInstalledAppVersion(),
            pendingDraftCount = pendingDraftCount,
            onBack = null,
            onOpenLedgers = { navController.navigate(LedgerRoutes.LEDGER_MANAGE) },
            onOpenAccounts = { if (ready) navController.navigate(LedgerRoutes.accountManage(ledgerId)) },
            onOpenCategories = { if (ready) navController.navigate(LedgerRoutes.categoryManage(ledgerId)) },
            onOpenTags = { if (ready) navController.navigate(LedgerRoutes.tagManage(ledgerId)) },
            onOpenBackup = onOpenBackup,
            onOpenAutoBook = { navController.navigate(LedgerRoutes.AUTO_BOOK) },
            onOpenAiModel = onOpenAiModel,
            onOpenAiRules = onOpenAiRules,
            onOpenRecurring = { if (ready) navController.navigate(LedgerRoutes.recurringList(ledgerId)) },
            onOpenDrafts = { if (ready) navController.navigate(LedgerRoutes.drafts(ledgerId)) },
            onOpenLoans = { if (ready) navController.navigate(LedgerRoutes.loanList(ledgerId)) },
        )
    }

    composable(
        route = LedgerRoutes.SETTINGS,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }),
    ) { entry ->
        val ledgerId = entry.arguments?.getString(LedgerRoutes.ARG_LEDGER_ID).orEmpty()
        val ready = ledgerId.isNotBlank() && ledgerId != "none"
        SettingsScreen(
            ledgerReady = ready,
            appVersion = rememberInstalledAppVersion(),
            onBack = { navController.popBackStack() },
            onOpenLedgers = { navController.navigate(LedgerRoutes.LEDGER_MANAGE) },
            onOpenAccounts = { if (ready) navController.navigate(LedgerRoutes.accountManage(ledgerId)) },
            onOpenCategories = { if (ready) navController.navigate(LedgerRoutes.categoryManage(ledgerId)) },
            onOpenTags = { if (ready) navController.navigate(LedgerRoutes.tagManage(ledgerId)) },
            onOpenBackup = onOpenBackup,
            onOpenAutoBook = { navController.navigate(LedgerRoutes.AUTO_BOOK) },
            onOpenAiModel = onOpenAiModel,
            onOpenAiRules = onOpenAiRules,
            onOpenRecurring = { if (ready) navController.navigate(LedgerRoutes.recurringList(ledgerId)) },
            onOpenDrafts = { if (ready) navController.navigate(LedgerRoutes.drafts(ledgerId)) },
            onOpenLoans = { if (ready) navController.navigate(LedgerRoutes.loanList(ledgerId)) },
        )
    }

    composable(LedgerRoutes.HOME) {
        HomeScreen(
            onOpenSavings = { navController.navigate(LedgerRoutes.savings(it)) },
            onHomeLayout = { navController.navigate(LedgerRoutes.homeLayout(it)) },
            onEditTxn = { ledger, txn -> navController.navigate(LedgerRoutes.txnEdit(ledger, txn)) },
            onOpenBudget = { navController.navigate(LedgerRoutes.budget(it)) },
            onOpenSearch = onOpenSearch,
            onOpenRange = onOpenRange,
            onOpenDrafts = { navController.navigate(LedgerRoutes.drafts(it)) },
            onOpenAuto = { navController.navigate(LedgerRoutes.AUTO_BOOK) },
            onAddTxn = { ledgerId -> navController.navigate(LedgerRoutes.txnEdit(ledgerId)) },
            onOpenTxns = { ledgerId -> navController.navigate(LedgerRoutes.txnList(ledgerId)) },
            onOpenLedgers = { navController.navigate(LedgerRoutes.LEDGER_MANAGE) },
            onOpenAccounts = { onOpenAssets() },
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
            navArgument(LedgerRoutes.ARG_DATE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("accountId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("entryType") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) {
        TxnEditScreen(onBack = { navController.popBackStack() })
    }

    composable(LedgerRoutes.LEDGER_MANAGE) {
        LedgerManageScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = LedgerRoutes.ACCOUNT_MANAGE,
        arguments = listOf(navArgument(LedgerRoutes.ARG_LEDGER_ID) { type = NavType.StringType }, navArgument("editAccountId") { type = NavType.StringType; nullable = true; defaultValue = null }),
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
