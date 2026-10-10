package com.han1997.vipledger.feature.ledger.ui.investment

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.ActionRow
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.PrimaryButton
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.model.InvestmentProgress
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.feature.ledger.R

/** 理财台账页：金额台账，不接行情、不自动估值。 */
@Composable
fun InvestmentScreen(
    onBack: () -> Unit,
    viewModel: InvestmentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InvestmentContent(state, viewModel::onAction, onBack)
}

@Composable
internal fun InvestmentContent(
    state: InvestmentUiState,
    onAction: (InvestmentAction) -> Unit,
    onBack: () -> Unit,
) {
    VipLedgerScaffold(topBar = { VipLedgerTopBar(title = stringResource(R.string.investment_title), onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(VipLedgerTokens.Space4),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item {
                    EmptyState(
                        message = stringResource(R.string.ledger_load_failed),
                        actionLabel = stringResource(R.string.ledger_retry),
                        onAction = { onAction(InvestmentAction.Retry) },
                    )
                }
                else -> {
                    item { Text(stringResource(R.string.investment_hint), style = MaterialTheme.typography.bodySmall) }
                    if (state.availableAccounts.isEmpty()) {
                        item { ActionRow(title = stringResource(R.string.investment_need_account)) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                            PrimaryButton(stringResource(R.string.investment_create), { onAction(InvestmentAction.Create) }, Modifier.weight(1f), enabled = !state.busy)
                            FilterChip(selected = state.showClosed, onClick = { onAction(InvestmentAction.ToggleClosed) }, label = { Text(stringResource(R.string.investment_show_closed)) })
                        }
                    }
                    item { Text(stringResource(R.string.investment_outstanding_total, state.outstandingTotal.toDisplayString()), style = MaterialTheme.typography.titleMedium) }
                    item { Text(stringResource(R.string.investment_realized_total, state.realizedTotal.toDisplayString()), style = MaterialTheme.typography.bodyMedium) }
                    if (state.writeFailed && state.editor == null && state.operation == null) {
                        item { Text(state.errorKey ?: stringResource(R.string.investment_write_failed), color = MaterialTheme.colorScheme.error) }
                    }
                    if (state.visible.isEmpty()) {
                        item { EmptyState(message = stringResource(R.string.investment_empty)) }
                    } else {
                        items(state.visible, key = { it.investment.id }) { progress ->
                            InvestmentCard(progress, state.busy, onAction)
                        }
                    }
                }
            }
        }
    }
    state.editor?.let { editor ->
        EditorDialog(editor, state, onAction)
    }
    state.operation?.let { operation ->
        OperationDialog(operation, state, onAction)
    }
}

@Composable
private fun InvestmentCard(progress: InvestmentProgress, busy: Boolean, onAction: (InvestmentAction) -> Unit) {
    val summary = progress.summary
    GroupCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(progress.investment.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.investment_summary,
                        summary.invested.toDisplayString(),
                        summary.realizedNet.toDisplayString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AmountText(amount = summary.principalOutstanding)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
            TextButton(onClick = { onAction(InvestmentAction.Operate(progress.investment.id, InvestmentLinkKind.INVEST)) }, enabled = !busy) {
                Text(stringResource(R.string.investment_add))
            }
            TextButton(onClick = { onAction(InvestmentAction.Operate(progress.investment.id, InvestmentLinkKind.REDEEM)) }, enabled = !busy) {
                Text(stringResource(R.string.investment_redeem))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
            TextButton(onClick = { onAction(InvestmentAction.Operate(progress.investment.id, InvestmentLinkKind.INCOME)) }, enabled = !busy) {
                Text(stringResource(R.string.investment_gain))
            }
            TextButton(onClick = { onAction(InvestmentAction.Operate(progress.investment.id, InvestmentLinkKind.EXPENSE)) }, enabled = !busy) {
                Text(stringResource(R.string.investment_loss))
            }
            TextButton(onClick = { onAction(InvestmentAction.SetClosed(progress.investment.id, !progress.investment.closed)) }, enabled = !busy) {
                Text(stringResource(if (progress.investment.closed) R.string.investment_reopen else R.string.investment_close))
            }
        }
    }
}

@Composable
private fun EditorDialog(editor: InvestmentEditor, state: InvestmentUiState, onAction: (InvestmentAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.busy) onAction(InvestmentAction.Dismiss) },
        title = { Text(stringResource(R.string.investment_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                OutlinedTextField(editor.name, { onAction(InvestmentAction.EditorChanged(editor.copy(name = it))) }, label = { Text(stringResource(R.string.investment_name)) }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(editor.amount, { onAction(InvestmentAction.EditorChanged(editor.copy(amount = it))) }, label = { Text(stringResource(R.string.investment_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.busy)
                Text(stringResource(R.string.investment_cash_account))
                AccountPicker(state.availableAccounts.map { it.id to it.name }, editor.cashAccountId, !state.busy) {
                    onAction(InvestmentAction.EditorChanged(editor.copy(cashAccountId = it)))
                }
                OutlinedTextField(editor.remark, { onAction(InvestmentAction.EditorChanged(editor.copy(remark = it))) }, label = { Text(stringResource(R.string.investment_remark)) }, singleLine = true, enabled = !state.busy)
                if (state.writeFailed) Text(state.errorKey ?: stringResource(R.string.investment_write_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(InvestmentAction.Save) }, enabled = editor.valid && !state.busy) },
        dismissButton = { TextButton(onClick = { onAction(InvestmentAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun OperationDialog(form: InvestmentOperation, state: InvestmentUiState, onAction: (InvestmentAction) -> Unit) {
    val progress = state.investments.firstOrNull { it.investment.id == form.investmentId }
    val outstanding = progress?.summary?.principalOutstanding
    val amount = form.money
    val valid = amount != null && form.cashAccountId.isNotBlank() &&
        (form.kind != InvestmentLinkKind.REDEEM || (outstanding != null && amount <= outstanding))
    val titleRes = when (form.kind) {
        InvestmentLinkKind.INVEST -> R.string.investment_add_title
        InvestmentLinkKind.REDEEM -> R.string.investment_redeem_title
        InvestmentLinkKind.INCOME -> R.string.investment_gain_title
        InvestmentLinkKind.EXPENSE -> R.string.investment_loss_title
    }
    val confirmTitle = stringResource(titleRes, progress?.investment?.name.orEmpty())
    AlertDialog(
        onDismissRequest = { if (!state.busy) onAction(InvestmentAction.Dismiss) },
        title = { Text(confirmTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                Text(stringResource(R.string.investment_cash_account))
                AccountPicker(state.availableAccounts.map { it.id to it.name }, form.cashAccountId, !state.busy) {
                    onAction(InvestmentAction.OperationChanged(form.copy(cashAccountId = it)))
                }
                OutlinedTextField(
                    form.amount,
                    { onAction(InvestmentAction.OperationChanged(form.copy(amount = it))) },
                    label = { Text(stringResource(R.string.investment_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !state.busy,
                )
                if (form.kind == InvestmentLinkKind.REDEEM) {
                    Text(stringResource(R.string.investment_redeem_hint), style = MaterialTheme.typography.bodySmall)
                    outstanding?.let { Text(stringResource(R.string.investment_principal, it.toDisplayString()), style = MaterialTheme.typography.bodySmall) }
                }
                if (state.writeFailed) Text(state.errorKey ?: stringResource(R.string.investment_write_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(InvestmentAction.ConfirmOperation(confirmTitle)) }, enabled = valid && !state.busy) },
        dismissButton = { TextButton(onClick = { onAction(InvestmentAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun AccountPicker(options: List<Pair<String, String>>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.investment_choose_account))
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(id) }) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InvestmentPreview() {
    VipLedgerTheme { InvestmentContent(InvestmentUiState(loading = false), {}, {}) }
}