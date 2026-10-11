package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.format.accountTypeLabel
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.domain.model.AssetGroup
import com.han1997.vipledger.feature.ledger.R

@Composable
fun AssetsScreen(onAccount: (String, String) -> Unit, onManage: (String) -> Unit, onSavings: (String) -> Unit, onReimburse: (String) -> Unit, onLending: (String) -> Unit, onLoans: (String) -> Unit, onInvest: (String) -> Unit, onLedgers: () -> Unit, onTrend: () -> Unit, viewModel: AssetsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssetsContent(state, viewModel::onAction, onAccount, onManage, onSavings, onReimburse, onLending, onLoans, onInvest, onLedgers, onTrend)
}

@Composable
internal fun AssetsContent(state: AssetsUiState, onAction: (AssetsAction) -> Unit, onAccount: (String, String) -> Unit, onManage: (String) -> Unit, onSavings: (String) -> Unit, onReimburse: (String) -> Unit, onLending: (String) -> Unit, onLoans: (String) -> Unit, onInvest: (String) -> Unit, onLedgers: () -> Unit, onTrend: () -> Unit) {
    VipLedgerScaffold(topBar = { VipLedgerTopBar(title = stringResource(R.string.wealth_assets), actions = { TextButton(onClick = onLedgers) { Text(state.ledgerName.ifBlank { stringResource(R.string.home_switch_ledger) }) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            val report = state.report
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(AssetsAction.Retry) }) }
                report == null || state.ledgerId == null -> item { EmptyState(message = stringResource(R.string.home_no_ledger), actionLabel = stringResource(R.string.ledger_manage_add), onAction = onLedgers) }
                else -> {
                    item {
                        val colors = vipLedgerAccentColors()
                        HeroCard {
                            Text(stringResource(R.string.home_net_worth), color = colors.onHeroMuted)
                            AmountText(report.netWorth, style = displayHero, color = colors.onHero)
                            Text(stringResource(R.string.wealth_totals, report.assets.toDisplayString(), report.debt.toDisplayString()), color = colors.onHero)
                        }
                    }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                        PrimaryButton(stringResource(R.string.wealth_savings), { onSavings(state.ledgerId) }, Modifier.weight(1f))
                        SecondaryButton(stringResource(R.string.wealth_manage), { onManage(state.ledgerId) }, Modifier.weight(1f))
                    } }
                    item { GroupCard {
                        ActionRow(title = stringResource(R.string.wealth_trend), subtitle = stringResource(R.string.wealth_trend_hint), onClick = onTrend)
                        ActionRow(title = stringResource(R.string.reimburse_title), subtitle = stringResource(R.string.reimburse_pending, state.reimbursementPending.toDisplayString()), onClick = { onReimburse(state.ledgerId) })
                        ActionRow(title = stringResource(R.string.lending_title), subtitle = stringResource(R.string.lending_outstanding, state.lendingOutstanding.toDisplayString()) + if (state.lendingActive > 0) " · " + state.lendingActive else "", onClick = { onLending(state.ledgerId) })
                        ActionRow(title = stringResource(R.string.wealth_installment), subtitle = stringResource(R.string.wealth_installment_remaining, state.installmentOutstanding.toDisplayString()) + if (state.installmentActive > 0) " · " + state.installmentActive else "", onClick = { onLoans(state.ledgerId) })
                        ActionRow(title = stringResource(R.string.wealth_investment), subtitle = stringResource(R.string.wealth_investment_outstanding, state.investmentPrincipal.toDisplayString()) + if (state.investmentActive > 0) " · " + state.investmentActive else "", onClick = { onInvest(state.ledgerId) })
                    } }
                    item { FilterChip(selected = state.showArchived, onClick = { onAction(AssetsAction.ToggleArchived) }, label = { Text(stringResource(R.string.account_manage_show_archived)) }) }
                    val accounts = report.accounts.filter { state.showArchived || !it.isArchived }
                    if (accounts.isEmpty()) item { EmptyState(message = stringResource(R.string.empty_accounts), actionLabel = stringResource(R.string.wealth_manage), onAction = { onManage(state.ledgerId) }) }
                    AssetGroup.entries.forEach { group ->
                        val rows = accounts.filter { AssetGroup.of(it.type) == group }
                        if (rows.isNotEmpty()) {
                            item(key = group) { SectionHeader(assetGroupName(group)) }
                            items(rows, key = { it.id }) { account ->
                                GroupCard { ActionRow(title = account.name, subtitle = accountTypeLabel(account.type) + if (account.isArchived) " · " + stringResource(R.string.wealth_archived) else if (!account.includeInNetWorth) " · " + stringResource(R.string.wealth_excluded) else "", trailingText = account.currentBalance.toDisplayString(), onClick = { onAccount(state.ledgerId, account.id) }) }
                            }
                        }
                    }
                    item { Text(stringResource(R.string.wealth_baseline_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun assetGroupName(group: AssetGroup) = stringResource(when (group) {
    AssetGroup.FUNDS -> R.string.wealth_group_funds
    AssetGroup.CREDIT -> R.string.wealth_group_credit
    AssetGroup.PREPAID -> R.string.wealth_group_prepaid
    AssetGroup.RECEIVABLE_PAYABLE -> R.string.wealth_group_receivables
    AssetGroup.LOANS -> R.string.wealth_group_loans
    AssetGroup.INVESTMENTS -> R.string.wealth_group_investments
})

@Preview(showBackground = true)
@Composable
private fun AssetsPreview() { VipLedgerTheme { AssetsContent(AssetsUiState(loading = false), {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}) } }
