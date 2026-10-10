package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.format.accountTypeLabel
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
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(AccountDetailAction.Retry) }) }
                account == null -> item { EmptyState(stringResource(R.string.wealth_unavailable)) }
                else -> {
                    item { GroupCard { Column(Modifier.padding(VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                        Text(accountTypeLabel(account.type))
                        AmountText(account.currentBalance, style = displayAmount)
                        if (!account.includeInNetWorth || account.isArchived) Text(stringResource(R.string.wealth_excluded))
                        if (account.isArchived) Text(stringResource(R.string.wealth_archived))
                    } } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                        PrimaryButton(stringResource(R.string.wealth_transfer), { onTransfer(account.ledgerId, account.id) }, Modifier.weight(1f), enabled = account.isUsable)
                        SecondaryButton(stringResource(R.string.wealth_adjust), { onAction(AccountDetailAction.Adjust) }, Modifier.weight(1f), enabled = account.isUsable && !state.busy)
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                        TextButton(onClick = { onEdit(account.ledgerId, account.id) }, enabled = !state.busy) { Text(stringResource(R.string.wealth_edit_account)) }
                        TextButton(onClick = { onAction(AccountDetailAction.Archive(!account.isArchived)) }, enabled = !state.busy) { Text(stringResource(if (account.isArchived) R.string.wealth_restore else R.string.wealth_archive)) }
                    } }
                    if (state.writeFailed) item { Text(stringResource(R.string.wealth_write_failed), color = MaterialTheme.colorScheme.error) }
                    state.baseline?.let { baseline -> item { Text(stringResource(R.string.wealth_baseline, fullDateLabel(baseline.capturedAt.toLocalDate(TimeZone.currentSystemDefault())), baseline.balance.toDisplayString()), style = MaterialTheme.typography.bodySmall) } }
                    item { SectionHeader(stringResource(R.string.wealth_transactions)) }
                    if (state.txns.isEmpty()) item { EmptyState(stringResource(R.string.wealth_no_transactions)) }
                    items(state.txns, key = { it.id }) { txn -> GroupCard { TxnLine(txn, txn.categoryId?.let(state.categoryNames::get), state.accountNames[txn.accountId], txn.tagIds.mapNotNull(state.tagNames::get), onClick = { onEditTxn(account.ledgerId, txn.id) }) } }
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
@Preview(showBackground = true)
@Composable
private fun AccountDetailPreview() { VipLedgerTheme { AccountDetailContent(AccountDetailUiState(loading = false), {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }) } }
