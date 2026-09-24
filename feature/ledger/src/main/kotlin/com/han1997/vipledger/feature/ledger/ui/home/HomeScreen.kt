package com.han1997.vipledger.feature.ledger.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.IncomeGreen
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.HomeOverview
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.TxnLine

/** 首页概览：净资产 / 本月收支 / 今日支出 / 最近账目。 */
@Composable
fun HomeScreen(
    onAddTxn: (String) -> Unit,
    onOpenTxns: (String) -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenAccounts: (String) -> Unit,
    onOpenCategories: (String) -> Unit,
    onOpenTags: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onAction = viewModel::onAction,
        onAddTxn = onAddTxn,
        onOpenTxns = onOpenTxns,
        onOpenLedgers = onOpenLedgers,
        onOpenAccounts = onOpenAccounts,
        onOpenCategories = onOpenCategories,
        onOpenTags = onOpenTags,
        modifier = modifier,
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onAddTxn: (String) -> Unit,
    onOpenTxns: (String) -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenAccounts: (String) -> Unit,
    onOpenCategories: (String) -> Unit,
    onOpenTags: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledgerId = state.ledgerId
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.home_title),
                actions = {
                    LedgerSwitcher(
                        state = state,
                        onAction = onAction,
                        onOpenLedgers = onOpenLedgers,
                    )
                    ManageMenu(
                        enabled = ledgerId != null,
                        onOpenAccounts = { ledgerId?.let(onOpenAccounts) },
                        onOpenCategories = { ledgerId?.let(onOpenCategories) },
                        onOpenTags = { ledgerId?.let { onOpenTags(it) } },
                    )
                },
            )
        },
        floatingActionButton = {
            if (ledgerId != null) {
                ExtendedFloatingActionButton(
                    onClick = { onAddTxn(ledgerId) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(text = stringResource(R.string.home_add_txn)) },
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.overview == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    message = stringResource(R.string.home_no_ledger),
                    actionLabel = stringResource(R.string.ledger_manage_add),
                    onAction = onOpenLedgers,
                )
            }

            else -> HomeBody(
                overview = state.overview,
                categoryNames = state.categoryNames,
                accountNames = state.accountNames,
                tagNames = state.tagNames,
                modifier = Modifier.padding(padding),
                onOpenTxns = { onOpenTxns(state.overview.ledgerId) },
            )
        }
    }
}

@Composable
private fun HomeBody(
    overview: HomeOverview,
    categoryNames: Map<String, String>,
    accountNames: Map<String, String>,
    tagNames: Map<String, String>,
    onOpenTxns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NetWorthCard(overview) }
        item { MonthCard(overview) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.home_recent_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onOpenTxns) {
                    Text(text = stringResource(R.string.home_see_all))
                }
            }
        }
        if (overview.recentTxns.isEmpty()) {
            item { EmptyState(message = stringResource(R.string.home_recent_empty)) }
        } else {
            items(items = overview.recentTxns.take(RECENT_PREVIEW), key = { it.id }) { txn ->
                TxnLine(
                    txn = txn,
                    categoryName = txn.categoryId?.let { categoryNames[it] },
                    accountName = accountNames[txn.accountId],
                    tagNames = txn.tagIds.mapNotNull { tagNames[it] },
                    onClick = onOpenTxns,
                )
            }
        }
    }
}

@Composable
private fun NetWorthCard(overview: HomeOverview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.home_net_worth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.home_account_count, overview.usableAccountCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AmountText(
                amount = overview.netWorth,
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabeledAmount(
                    label = stringResource(R.string.home_total_debt),
                    amount = overview.totalDebt,
                    modifier = Modifier.weight(1f),
                )
                LabeledAmount(
                    label = stringResource(R.string.home_today_expense),
                    amount = overview.todayExpense,
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MonthCard(overview: HomeOverview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledAmount(
                label = stringResource(R.string.home_month_income),
                amount = overview.monthIncome,
                color = IncomeGreen,
                modifier = Modifier.weight(1f),
            )
            LabeledAmount(
                label = stringResource(R.string.home_month_expense),
                amount = overview.monthExpense,
                color = ExpenseRed,
                modifier = Modifier.weight(1f),
            )
            LabeledAmount(
                label = stringResource(R.string.home_month_balance),
                amount = overview.monthBalance,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LabeledAmount(
    label: String,
    amount: Money,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        AmountText(amount = amount, color = color)
    }
}

@Composable
private fun LedgerSwitcher(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenLedgers: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(text = state.ledgerName.ifBlank { stringResource(R.string.home_switch_ledger) })
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.ledgers.forEach { ledger ->
                DropdownMenuItem(
                    text = { Text(text = ledger.name) },
                    trailingIcon = {
                        if (ledger.id == state.ledgerId) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onAction(HomeAction.SelectLedger(ledger.id))
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.ledger_manage_title)) },
                onClick = {
                    expanded = false
                    onOpenLedgers()
                },
            )
        }
    }
}

@Composable
private fun ManageMenu(
    enabled: Boolean,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_manage))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.account_manage_title)) },
                onClick = { expanded = false; onOpenAccounts() },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.category_manage_title)) },
                onClick = { expanded = false; onOpenCategories() },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.tag_manage_title)) },
                onClick = { expanded = false; onOpenTags() },
            )
        }
    }
}

private const val RECENT_PREVIEW: Int = 5

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    VipLedgerTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                ledgerId = "l1",
                ledgerName = "默认账本",
                ledgers = listOf(
                    Ledger(id = "l1", name = "默认账本", isDefault = true, createdAt = 0, updatedAt = 0),
                ),
                overview = HomeOverview(
                    ledgerId = "l1",
                    ledgerName = "默认账本",
                    netWorth = Money.ofCents(1234567),
                    totalDebt = Money.ofCents(250000),
                    monthIncome = Money.ofCents(800000),
                    monthExpense = Money.ofCents(321100),
                    monthCount = 20,
                    todayExpense = Money.ofCents(3800),
                    usableAccountCount = 3,
                    recentTxns = listOf(
                        Txn(
                            id = "t1",
                            ledgerId = "l1",
                            type = TransactionType.EXPENSE,
                            amount = Money.ofCents(3800),
                            accountId = "a1",
                            title = "午餐",
                            occurredAt = 0,
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                    ),
                ),
                accountNames = mapOf("a1" to "现金"),
                categoryNames = mapOf("c1" to "餐饮"),
            ),
            onAction = {},
            onAddTxn = {},
            onOpenTxns = {},
            onOpenLedgers = {},
            onOpenAccounts = {},
            onOpenCategories = {},
            onOpenTags = {},
        )
    }
}