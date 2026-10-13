package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.format.accountTypeLabel
import com.han1997.vipledger.core.designsystem.format.cardNoLast4
import com.han1997.vipledger.core.designsystem.format.formatCardNo
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.TimeZone

@Composable
fun AccountDetailScreen(onBack: () -> Unit, onEdit: (String, String) -> Unit, onTransfer: (String, String) -> Unit, onEditTxn: (String, String) -> Unit, viewModel: AccountDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountDetailContent(state, viewModel::onAction, onBack, onEdit, onTransfer, onEditTxn)
}

@Composable
internal fun AccountDetailContent(state: AccountDetailUiState, onAction: (AccountDetailAction) -> Unit, onBack: () -> Unit, onEdit: (String, String) -> Unit, onTransfer: (String, String) -> Unit, onEditTxn: (String, String) -> Unit) {
    val account = state.account
    VipLedgerScaffold(topBar = { VipLedgerTopBar(title = account?.name ?: stringResource(R.string.wealth_account_detail), onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = VipLedgerTokens.Space4,
                end = VipLedgerTokens.Space4,
                top = VipLedgerTokens.Space2,
                bottom = VipLedgerTokens.Space6,
            ),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(AccountDetailAction.Retry) }) }
                account == null -> item { EmptyState(stringResource(R.string.wealth_unavailable)) }
                else -> {
                    item { AccountHero(
                        name = account.name,
                        typeLabel = accountTypeLabel(account.type),
                        balance = if (account.isDebt) account.debtAmount else account.currentBalance,
                        negative = account.isDebt || account.currentBalance.cents < 0,
                        cardNo = account.cardNo,
                    ) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                            PrimaryButton(stringResource(R.string.wealth_transfer), { onTransfer(account.ledgerId, account.id) }, Modifier.weight(1f), enabled = account.isUsable)
                            SecondaryButton(stringResource(R.string.wealth_adjust), { onAction(AccountDetailAction.Adjust) }, Modifier.weight(1f), enabled = account.isUsable && !state.busy)
                        }
                    }
                    item {
                        GroupCard {
                            ActionRow(title = stringResource(R.string.wealth_edit_account), onClick = { onEdit(account.ledgerId, account.id) }, enabled = !state.busy)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ActionRow(
                                title = stringResource(if (account.isArchived) R.string.wealth_restore else R.string.wealth_archive),
                                onClick = { onAction(AccountDetailAction.Archive(!account.isArchived)) },
                                enabled = !state.busy,
                            )
                        }
                    }
                    if (account.cardNo != null) {
                        item {
                            GroupCard {
                                InfoRow(stringResource(R.string.account_manage_card_no), formatCardNo(account.cardNo))
                            }
                        }
                    }
                    if (account.remark != null) {
                        val remark = account.remark
                        item { GroupCard { InfoRow(stringResource(R.string.txn_edit_field_remark), remark.orEmpty()) } }
                    }
                    if (state.writeFailed) {
                        item { Text(stringResource(R.string.wealth_write_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    state.baseline?.let { baseline ->
                        item {
                            Text(
                                text = stringResource(R.string.wealth_baseline, fullDateLabel(baseline.capturedAt.toLocalDate(TimeZone.currentSystemDefault())), baseline.balance.toDisplayString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item { SectionHeader(stringResource(R.string.wealth_transactions)) }
                    if (state.txns.isEmpty()) item { EmptyState(stringResource(R.string.wealth_no_transactions)) }
                    items(state.txns, key = { it.id }) { txn ->
                        TxnLine(
                            txn = txn,
                            categoryName = txn.categoryId?.let(state.categoryNames::get),
                            accountName = state.accountNames[txn.accountId],
                            tagNames = txn.tagIds.mapNotNull(state.tagNames::get),
                            onClick = { onEditTxn(account.ledgerId, txn.id) },
                        )
                    }
                }
            }
        }
    }
    state.balanceInput?.let { value ->
        val title = stringResource(R.string.wealth_adjust)
        AlertDialog(onDismissRequest = { if (!state.busy) onAction(AccountDetailAction.Dismiss) }, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            Text(stringResource(R.string.wealth_adjust_hint))
            OutlinedTextField(value, { onAction(AccountDetailAction.Input(it)) }, label = { Text(stringResource(R.string.wealth_current_balance)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.busy)
            if (state.writeFailed) Text(stringResource(R.string.wealth_write_failed), color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(AccountDetailAction.SaveAdjustment(title)) }, enabled = !state.busy && Money.parse(value) != null) }, dismissButton = { TextButton(onClick = { onAction(AccountDetailAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } })
    }
}

/** 账户 hero(原型 .acct-hero):洗色图标 + 名称 + 类型·尾号 + 衬线余额。 */
@Composable
private fun AccountHero(name: String, typeLabel: String, balance: Money, negative: Boolean, cardNo: String?) {
    val colors = vipLedgerAccentColors()
    val shape = RoundedCornerShape(VipLedgerTokens.RadiusCard)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(colors.heroGradientStart, colors.heroGradientEnd)),
                shape = shape,
            )
            .padding(VipLedgerTokens.Space5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(colors.accentAmber, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1), style = serifTitle, color = colors.heroGradientEnd)
        }
        Text(name, style = serifTitle, color = colors.onHero)
        val subtitle = buildString {
            append(typeLabel)
            cardNo?.let { append(" · " + stringResource(R.string.account_manage_card_no_short, cardNoLast4(it))) }
        }
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onHeroMuted)
        AmountText(
            amount = balance,
            style = displayHero,
            color = if (negative) colors.accentCoral else colors.onHero,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VipLedgerTokens.Space4, vertical = VipLedgerTokens.Space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountDetailPreview() { VipLedgerTheme { AccountDetailContent(AccountDetailUiState(loading = false), {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }) } }