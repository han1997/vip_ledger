package com.han1997.vipledger.feature.ledger.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import com.han1997.vipledger.core.domain.model.HomeCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.component.chart.BarChart
import com.han1997.vipledger.core.designsystem.component.chart.BarGroup
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.budget.BudgetProgressContent

@Composable
fun HomeScreen(
    onAddTxn: (String) -> Unit,
    onOpenTxns: (String) -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenAccounts: (String) -> Unit,
    onOpenLoanDetail: (String, String) -> Unit,
    onEditTxn: (String, String) -> Unit,
    onOpenBudget: (String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenRange: (String, DateRange) -> Unit,
    onOpenDrafts: (String) -> Unit,
    onOpenAuto: () -> Unit,
    onOpenSavings: (String) -> Unit,
    onHomeLayout: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, viewModel::onAction, onAddTxn, onOpenTxns, onOpenLedgers, onOpenAccounts, onOpenLoanDetail, onEditTxn, onOpenBudget, onOpenSearch, onOpenRange, onOpenDrafts, onOpenAuto, onOpenSavings, onHomeLayout, modifier)
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onAddTxn: (String) -> Unit,
    onOpenTxns: (String) -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenAccounts: (String) -> Unit,
    onOpenLoanDetail: (String, String) -> Unit,
    onEditTxn: (String, String) -> Unit,
    onOpenBudget: (String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenRange: (String, DateRange) -> Unit,
    onOpenDrafts: (String) -> Unit,
    onOpenAuto: () -> Unit,
    onOpenSavings: (String) -> Unit,
    onHomeLayout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledger = state.ledgerId
    VipLedgerScaffold(modifier = modifier.fillMaxSize(), topBar = {
        VipLedgerTopBar(title = stringResource(R.string.home_title), actions = {
            LedgerSwitcher(state, onAction, onOpenLedgers)
            IconButton(onClick = { ledger?.let(onOpenSearch) }, enabled = ledger != null) { Icon(Icons.Filled.Search, stringResource(R.string.ledger_home_search)) }
            IconButton(onClick = { ledger?.let(onHomeLayout) }, enabled = ledger != null) { Icon(Icons.Filled.Tune, stringResource(R.string.wealth_home_layout)) }
        })
    }) { padding ->
        val overview = state.overview
        when {
            state.failed -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(HomeAction.Retry) }) }
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState() }
            ledger == null || overview == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EmptyState(message = stringResource(R.string.home_no_ledger), actionLabel = stringResource(R.string.ledger_manage_add), onAction = onOpenLedgers) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                item {
                    val colors = vipLedgerAccentColors()
                    HeroCard(modifier = Modifier.clickable(role = Role.Button) { onOpenAccounts(ledger) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                            HeroMetric(stringResource(R.string.ledger_home_assets), overview.totalAssets.toDisplayString(false), Modifier.weight(1f))
                            HeroMetric(stringResource(R.string.home_month_balance), overview.monthBalance.toDisplayString(false), Modifier.weight(1f))
                            HeroMetric(stringResource(R.string.ledger_home_budget_remaining), state.budget?.total?.remaining?.toDisplayString(false) ?: stringResource(R.string.ledger_home_no_budget), Modifier.weight(1f))
                        }
                        Text(stringResource(R.string.home_month_expense), color = colors.onHeroMuted, style = MaterialTheme.typography.labelMedium)
                        AmountText(overview.monthExpense, style = displayHero, color = colors.onHero)
                    }
                }
                item { PrimaryButton(stringResource(R.string.home_add_txn), { onAddTxn(ledger) }, Modifier.fillMaxWidth()) }
                state.cards.filter { it.visible }.chunked(2).forEach { row ->
                    item(key = row.joinToString { it.card.name }) {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                            row.forEach { setting ->
                                key(setting.card) {
                                    val tileModifier = Modifier.weight(1f).fillMaxHeight()
                                    when (setting.card) {
                                        HomeCard.BUDGET -> HomeTile(stringResource(R.string.ledger_home_budget_entry), { onOpenBudget(ledger) }, tileModifier) {
                                            val budget = state.budget?.total
                                            if (budget != null) BudgetProgressContent(budget)
                                            else Text(stringResource(R.string.ledger_budget_set_total), color = MaterialTheme.colorScheme.primary)
                                        }
                                        HomeCard.TODAY -> HomeTile(stringResource(R.string.ledger_home_today), {
                                            overview.lastSevenDays.lastOrNull()?.let { onOpenRange(ledger, DateRange(it.start, it.endInclusive)) }
                                        }, tileModifier) {
                                            Text(stringResource(R.string.ledger_home_income), style = MaterialTheme.typography.labelSmall)
                                            AmountText(overview.todayIncome, color = IncomeGreen, style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.ledger_home_expense), style = MaterialTheme.typography.labelSmall)
                                            AmountText(overview.todayExpense, color = ExpenseRed, style = MaterialTheme.typography.titleMedium)
                                        }
                                        HomeCard.SAVINGS -> HomeTile(stringResource(R.string.wealth_savings), { onOpenSavings(ledger) }, tileModifier) {
                                            val goal = state.savings.firstOrNull { !it.achieved } ?: state.savings.firstOrNull()
                                            if (goal == null) Text(stringResource(R.string.wealth_goal_new), color = MaterialTheme.colorScheme.primary)
                                            else {
                                                Text(goal.goal.name, style = MaterialTheme.typography.bodyMedium)
                                                AmountText(goal.saved, style = MaterialTheme.typography.titleMedium)
                                                LinearProgressIndicator(progress = { goal.fraction }, modifier = Modifier.fillMaxWidth())
                                                Text(goal.goal.target.toDisplayString(), style = MaterialTheme.typography.labelSmall)
                                                if (goal.achieved) Text(stringResource(R.string.wealth_achieved), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        HomeCard.WEEK -> HomeTile(stringResource(R.string.ledger_home_week), {
                                            val days = overview.lastSevenDays
                                            if (days.isNotEmpty()) onOpenRange(ledger, DateRange(days.first().start, days.last().endInclusive))
                                        }, tileModifier) {
                                            BarChart(overview.lastSevenDays.map { BarGroup(it.start.dayOfMonth.toString(), 0, it.expense.cents) }, height = VipLedgerTokens.Space8 * 2, maxLabels = 7, showLegend = false)
                                        }
                                        HomeCard.PENDING -> HomeTile(stringResource(R.string.ledger_home_pending), {
                                            if (state.pendingAutoCount > 0) onOpenAuto() else onOpenDrafts(ledger)
                                        }, tileModifier) {
                                            TextButton(onClick = { onOpenDrafts(ledger) }) { Text(stringResource(R.string.ledger_home_pending_count, state.pendingDraftCount)) }
                                            TextButton(onClick = onOpenAuto) { Text(stringResource(R.string.ledger_home_auto_count, state.pendingAutoCount)) }
                                            Text(stringResource(R.string.ledger_home_reminders_count, state.reminders.size), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (state.reminders.isNotEmpty()) item {
                    GroupCard { state.reminders.take(3).forEach { reminder ->
                        ActionRow(title = reminder.name, subtitle = fullDateLabel(reminder.dueDate), onClick = { reminder.planId?.let { onOpenLoanDetail(ledger, it) } ?: onOpenAccounts(ledger) })
                    } }
                }
                item { SectionHeader(stringResource(R.string.home_recent_title), actionLabel = stringResource(R.string.home_see_all), onActionClick = { onOpenTxns(ledger) }) }
                if (overview.recentGroups.isEmpty()) item { EmptyState(stringResource(R.string.home_recent_empty)) }
                overview.recentGroups.forEach { group ->
                    item(key = "day-${group.date}") { Text(fullDateLabel(group.date), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(group.txns, key = { it.id }) { txn ->
                        GroupCard { TxnLine(txn = txn, categoryName = txn.categoryId?.let(state.categoryNames::get), accountName = state.accountNames[txn.accountId], tagNames = txn.tagIds.mapNotNull(state.tagNames::get), onClick = { onEditTxn(ledger, txn.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier) {
    val colors = vipLedgerAccentColors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onHeroMuted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = colors.onHero)
    }
}

@Composable
private fun HomeTile(title: String, onClick: () -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    GroupCard(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(VipLedgerTokens.Space3).heightIn(min = VipLedgerTokens.Space8 * 3), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun LedgerSwitcher(state: HomeUiState, onAction: (HomeAction) -> Unit, onOpenLedgers: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(state.ledgerName.ifBlank { stringResource(R.string.home_switch_ledger) })
            Icon(Icons.Filled.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            state.ledgers.forEach { ledger -> DropdownMenuItem(text = { Text(ledger.name) }, onClick = { expanded = false; onAction(HomeAction.SelectLedger(ledger.id)) }) }
            DropdownMenuItem(text = { Text(stringResource(R.string.ledger_manage_title)) }, onClick = { expanded = false; onOpenLedgers() })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() { VipLedgerTheme { HomeContent(HomeUiState(isLoading = false), {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}, {}) } }
