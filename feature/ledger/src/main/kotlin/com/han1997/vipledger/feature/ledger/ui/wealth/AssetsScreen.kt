package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.format.accountTypeLabel
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.AssetGroup
import com.han1997.vipledger.core.domain.model.SavingsProgress
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R

@Composable
fun AssetsScreen(onAccount: (String, String) -> Unit, onManage: (String) -> Unit, onSavings: (String) -> Unit, onReimburse: (String) -> Unit, onLending: (String) -> Unit, onLoans: (String) -> Unit, onInvest: (String) -> Unit, onLedgers: () -> Unit, onTrend: () -> Unit, viewModel: AssetsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssetsContent(state, viewModel::onAction, onAccount, onManage, onSavings, onReimburse, onLending, onLoans, onInvest, onLedgers, onTrend)
}

@Composable
internal fun AssetsContent(state: AssetsUiState, onAction: (AssetsAction) -> Unit, onAccount: (String, String) -> Unit, onManage: (String) -> Unit, onSavings: (String) -> Unit, onReimburse: (String) -> Unit, onLending: (String) -> Unit, onLoans: (String) -> Unit, onInvest: (String) -> Unit, onLedgers: () -> Unit, onTrend: () -> Unit) {
    VipLedgerScaffold(
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.wealth_assets),
                actions = {
                    IconButton(onClick = onTrend) { Icon(Icons.Filled.ShowChart, stringResource(R.string.wealth_trend_entry)) }
                    IconButton(onClick = { state.ledgerId?.let(onManage) }, enabled = state.ledgerId != null) { Icon(Icons.Filled.Add, stringResource(R.string.wealth_add_account)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = VipLedgerTokens.Space4, end = VipLedgerTokens.Space4, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            val report = state.report
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(AssetsAction.Retry) }) }
                report == null || state.ledgerId == null -> item { EmptyState(message = stringResource(R.string.home_no_ledger), actionLabel = stringResource(R.string.ledger_manage_add), onAction = onLedgers) }
                else -> {
                    item { NetWorthHero(netWorth = report.netWorth, assets = report.assets, debt = report.debt) }

                    val accounts = report.accounts.filter { state.showArchived || !it.isArchived }
                    val assets = accounts.filter { !it.type.isDebt }
                    val debts = accounts.filter { it.type.isDebt }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.wealth_total_assets),
                            actionLabel = stringResource(R.string.wealth_manage),
                            onActionClick = { onManage(requireNotNull(state.ledgerId)) },
                        )
                    }
                    if (assets.isEmpty()) {
                        item { EmptyState(message = stringResource(R.string.empty_accounts), actionLabel = stringResource(R.string.wealth_manage), onAction = { onManage(requireNotNull(state.ledgerId)) }) }
                    } else {
                        item {
                            GroupCard {
                                assets.forEachIndexed { index, account ->
                                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    AccountRow(account, onAccount = { onAccount(state.ledgerId, account.id) })
                                }
                                GroupSubtotal(
                                    label = stringResource(R.string.wealth_group_subtotal, stringResource(R.string.wealth_total_assets)),
                                    amount = Money.sum(assets.map { it.currentBalance }),
                                )
                            }
                        }
                    }

                    if (debts.isNotEmpty()) {
                        item { SectionHeader(title = stringResource(R.string.wealth_total_debt)) }
                        item {
                            GroupCard {
                                debts.forEachIndexed { index, account ->
                                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    AccountRow(account, onAccount = { onAccount(state.ledgerId, account.id) })
                                }
                                GroupSubtotal(
                                    label = stringResource(R.string.wealth_group_subtotal, stringResource(R.string.wealth_total_debt)),
                                    amount = Money.sum(debts.map { it.currentBalance }),
                                    danger = true,
                                )
                            }
                        }
                    }

                    val archivedCount = report.accounts.count { it.isArchived }
                    if (archivedCount > 0) {
                        item {
                            EntryRow(
                                title = stringResource(R.string.wealth_archived_accounts),
                                trailing = stringResource(R.string.wealth_group_count, archivedCount),
                                onClick = { onAction(AssetsAction.ToggleArchived) },
                            )
                        }
                    }

                    item { SectionHeader(title = stringResource(R.string.wealth_savings)) }
                    if (state.savings.isEmpty()) {
                        item {
                            EntryRow(
                                title = stringResource(R.string.wealth_goal_new),
                                trailing = null,
                                onClick = { onSavings(requireNotNull(state.ledgerId)) },
                            )
                        }
                    } else {
                        state.savings.forEach { goal ->
                            item(key = goal.goal.id) {
                                SavingsGoalCard(goal, onClick = { onSavings(requireNotNull(state.ledgerId)) })
                            }
                        }
                    }

                    item { SectionHeader(title = stringResource(R.string.wealth_plan_group)) }
                    item {
                        GroupCard {
                            PlanRow(stringResource(R.string.reimburse_title), stringResource(R.string.reimburse_pending, state.reimbursementPending.toDisplayString()), { onReimburse(state.ledgerId) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PlanRow(stringResource(R.string.lending_title), stringResource(R.string.lending_outstanding, state.lendingOutstanding.toDisplayString()) + if (state.lendingActive > 0) " · " + state.lendingActive else "", { onLending(state.ledgerId) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PlanRow(stringResource(R.string.wealth_installment), stringResource(R.string.wealth_installment_remaining, state.installmentOutstanding.toDisplayString()) + if (state.installmentActive > 0) " · " + state.installmentActive else "", { onLoans(state.ledgerId) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PlanRow(stringResource(R.string.wealth_investment), stringResource(R.string.wealth_investment_outstanding, state.investmentPrincipal.toDisplayString()) + if (state.investmentActive > 0) " · " + state.investmentActive else "", { onInvest(state.ledgerId) })
                        }
                    }

                    item {
                        EntryRow(
                            title = stringResource(R.string.wealth_trend_entry),
                            trailing = stringResource(R.string.wealth_trend_entry_hint),
                            onClick = onTrend,
                        )
                    }
                    item { Text(stringResource(R.string.wealth_baseline_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** 净资产 hero(原型 .hero):大额 + 总资产/总负债分栏。 */
@Composable
private fun NetWorthHero(netWorth: Money, assets: Money, debt: Money) {
    val colors = vipLedgerAccentColors()
    HeroCard {
        Text(stringResource(R.string.wealth_assets_subtitle), style = MaterialTheme.typography.labelMedium, color = colors.onHeroMuted)
        Box(modifier = Modifier.padding(top = VipLedgerTokens.Space2)) {
            AmountText(amount = netWorth, style = displayHero, color = colors.onHero)
        }
        Box(
            modifier = Modifier
                .padding(vertical = VipLedgerTokens.Space4)
                .fillMaxWidth()
                .height(VipLedgerTokens.BorderThin)
                .background(Brush.horizontalGradient(listOf(colors.accentAmber, colors.accentAmber.copy(alpha = 0.04f)))),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space6)) {
            // 原型 assetsView 分栏内联色：总资产浅松绿 #8FD9B4、总负债浅赭红 #F0A99B。
            HeroSplit(stringResource(R.string.wealth_total_assets), assets, HeroAssetTint, colors.onHeroMuted, Modifier.weight(1f))
            HeroSplit(stringResource(R.string.wealth_total_debt), debt.abs(), HeroDebtTint, colors.onHeroMuted, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroSplit(label: String, amount: Money, onHero: Color, onHeroMuted: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = onHeroMuted)
        AmountText(amount = amount, style = displayAmount, color = onHero)
    }
}

/** 账户行(原型 .acct):洗色图标 + 名称/类型·备注 + 衬线余额。 */
@Composable
private fun AccountRow(account: Account, onAccount: () -> Unit) {
    val (wash, ink) = accountTone(account)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAccount)
            .padding(horizontal = VipLedgerTokens.Space4, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(wash),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.name.take(1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ink,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = buildString {
                append(accountTypeLabel(account.type))
                account.remark?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                if (account.isArchived) append(" · " + stringResource(R.string.wealth_archived))
                else if (!account.includeInNetWorth) append(" · " + stringResource(R.string.wealth_excluded))
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = account.currentBalance.toDisplayString(),
            style = displayAmount,
            color = if (account.currentBalance.cents < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun accountTone(account: Account): Pair<Color, Color> = when (AssetGroup.of(account.type)) {
    AssetGroup.FUNDS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    AssetGroup.CREDIT, AssetGroup.LOANS -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    AssetGroup.PREPAID -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    AssetGroup.RECEIVABLE_PAYABLE, AssetGroup.INVESTMENTS -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

/** 分组小计(原型 .group-total)。 */
@Composable
private fun GroupSubtotal(label: String, amount: Money, danger: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VipLedgerTokens.Space4, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = amount.toDisplayString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) ExpenseRed else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 储蓄目标卡(原型 .goal):名称 + 完成度 + 进度条 + 已存/目标。 */
@Composable
private fun SavingsGoalCard(goal: SavingsProgress, onClick: () -> Unit) {
    val colors = vipLedgerAccentColors()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusCard),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(goal.goal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${(goal.fraction * 100).toInt()}%", style = displayAmount, color = colors.accentAmber)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(VipLedgerTokens.RadiusFull)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(goal.fraction)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, colors.accentAmber)),
                            RoundedCornerShape(VipLedgerTokens.RadiusFull),
                        ),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.wealth_savings_progress, goal.saved.toDisplayString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.wealth_savings_target, goal.goal.target.toDisplayString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (goal.achieved) Text(stringResource(R.string.wealth_achieved), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 入口行(原型 .entry-row):洗色图标 + 标题 + 右侧说明。 */
@Composable
private fun EntryRow(title: String, trailing: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusCard),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VipLedgerTokens.Space4, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PlanRow(title: String, subtitle: String, onClick: () -> Unit) {
    ActionRow(title = title, subtitle = subtitle, onClick = onClick)
}

@Preview(showBackground = true)
@Composable
private fun AssetsPreview() { VipLedgerTheme { AssetsContent(AssetsUiState(loading = false), {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}) } }