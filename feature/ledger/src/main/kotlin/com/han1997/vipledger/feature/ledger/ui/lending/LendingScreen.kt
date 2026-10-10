package com.han1997.vipledger.feature.ledger.ui.lending

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.PrimaryButton
import com.han1997.vipledger.core.designsystem.component.SecondaryButton
import com.han1997.vipledger.core.designsystem.component.SectionHeader
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.model.LendingProgress
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.feature.ledger.R

@Composable
fun LendingScreen(
    onBack: () -> Unit,
    onEditTxn: (String, String) -> Unit,
    viewModel: LendingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LendingContent(state, viewModel::onAction, onBack, onEditTxn)
}

@Composable
internal fun LendingContent(
    state: LendingUiState,
    onAction: (LendingAction) -> Unit,
    onBack: () -> Unit,
    onEditTxn: (String, String) -> Unit,
) {
    VipLedgerScaffold(topBar = { VipLedgerTopBar(title = stringResource(R.string.lending_title), onBack = onBack) }) { padding ->
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
                        onAction = { onAction(LendingAction.Retry) },
                    )
                }
                else -> {
                    item { Text(stringResource(R.string.lending_settle_hint), style = MaterialTheme.typography.bodySmall) }
                    if (state.availableAccounts.isEmpty()) item { ActionRow(title = stringResource(R.string.lending_need_account)) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                            PrimaryButton(stringResource(R.string.lending_create), { onAction(LendingAction.Create) }, Modifier.weight(1f), enabled = !state.busy)
                            FilterChip(selected = state.showClosed, onClick = { onAction(LendingAction.ToggleClosed) }, label = { Text(stringResource(R.string.lending_show_closed)) })
                        }
                    }
                    item { Text(stringResource(R.string.lending_outstanding, state.outstandingTotal.toDisplayString()), style = MaterialTheme.typography.titleMedium) }
                    if (state.writeFailed && state.editor == null && state.settlement == null) {
                        item { Text(state.errorKey ?: stringResource(R.string.lending_write_failed), color = MaterialTheme.colorScheme.error) }
                    }
                    val visible = state.lendings.filter { state.showClosed || !it.record.closed }
                    if (visible.isEmpty()) {
                        item {
                            EmptyState(
                                message = stringResource(R.string.lending_empty),
                                actionLabel = stringResource(R.string.lending_create),
                                onAction = { onAction(LendingAction.Create) },
                            )
                        }
                    }
                    visible.forEach { progress ->
                        item(key = progress.record.id) { LendingCard(progress, { onAction(LendingAction.Select(progress.record.id)) }) }
                        if (state.selectedId == progress.record.id) {
                            item(key = "actions-${progress.record.id}") { LendingActions(progress, state, onAction) }
                            if (progress.txns.isNotEmpty()) {
                                item(key = "txns-header-${progress.record.id}") { SectionHeader(stringResource(R.string.lending_linked_txns)) }
                                items(progress.txns, key = { "lending-txn-${it.id}" }) { txn ->
                                    GroupCard {
                                        ActionRow(
                                            title = txn.title.ifBlank { stringResource(R.string.lending_linked_txns) },
                                            trailingText = txn.amount.toDisplayString(),
                                            onClick = { onEditTxn(progress.record.ledgerId, txn.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.editor?.let { form -> EditorDialog(form, state, onAction) }
    state.settlement?.let { form -> SettlementDialog(form, state, onAction) }
}

@Composable
private fun LendingCard(progress: LendingProgress, onClick: () -> Unit) {
    val record = progress.record
    GroupCard {
        ActionRow(
            title = record.counterparty,
            subtitle = listOfNotNull(
                directionLabel(record.direction),
                stringResource(
                    R.string.lending_progress,
                    record.principal.toDisplayString(),
                    progress.settledPrincipal.toDisplayString(),
                    progress.outstanding.toDisplayString(),
                ),
            ).joinToString(" · "),
            trailingText = when {
                record.closed -> stringResource(R.string.lending_closed)
                progress.settled -> stringResource(R.string.lending_settled)
                else -> null
            },
            onClick = onClick,
        )
        Column(Modifier.padding(horizontal = VipLedgerTokens.Space4).padding(bottom = VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
            if (progress.interestTotal.isPositive) Text(stringResource(R.string.lending_interest_total, progress.interestTotal.toDisplayString()), style = MaterialTheme.typography.bodySmall)
            record.dueDate?.let { Text(stringResource(R.string.lending_due_value, it.toString()), style = MaterialTheme.typography.bodySmall) }
            if (record.remark.isNotBlank()) Text(record.remark, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LendingActions(progress: LendingProgress, state: LendingUiState, onAction: (LendingAction) -> Unit) {
    val record = progress.record
    val canSettle = progress.active && state.availableAccounts.isNotEmpty() && !state.busy
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
            PrimaryButton(stringResource(R.string.lending_settle), { onAction(LendingAction.Settle(record.id)) }, Modifier.weight(1f), enabled = canSettle)
            SecondaryButton(stringResource(R.string.lending_interest), { onAction(LendingAction.Interest(record.id)) }, Modifier.weight(1f), enabled = !record.closed && state.availableAccounts.isNotEmpty() && !state.busy)
        }
        TextButton(onClick = { onAction(LendingAction.SetClosed(record.id, !record.closed)) }, enabled = !state.busy) {
            Text(stringResource(if (record.closed) R.string.lending_restore else R.string.lending_close))
        }
    }
}

@Composable
private fun EditorDialog(form: LendingEditor, state: LendingUiState, onAction: (LendingAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.busy) onAction(LendingAction.Dismiss) },
        title = { Text(stringResource(R.string.lending_create)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                    FilterChip(selected = form.direction == LendingDirection.LEND, onClick = { onAction(LendingAction.EditorChanged(form.copy(direction = LendingDirection.LEND))) }, label = { Text(stringResource(R.string.lending_direction_lend)) })
                    FilterChip(selected = form.direction == LendingDirection.BORROW, onClick = { onAction(LendingAction.EditorChanged(form.copy(direction = LendingDirection.BORROW))) }, label = { Text(stringResource(R.string.lending_direction_borrow)) })
                }
                OutlinedTextField(form.counterparty, { onAction(LendingAction.EditorChanged(form.copy(counterparty = it))) }, label = { Text(stringResource(R.string.lending_counterparty)) }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(form.principal, { onAction(LendingAction.EditorChanged(form.copy(principal = it))) }, label = { Text(stringResource(R.string.lending_principal)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.busy)
                Text(stringResource(R.string.lending_cash_account))
                AccountPicker(state.availableAccounts.map { it.id to it.name }, form.cashAccountId, !state.busy) { onAction(LendingAction.EditorChanged(form.copy(cashAccountId = it))) }
                OutlinedTextField(form.dueDate, { onAction(LendingAction.EditorChanged(form.copy(dueDate = it))) }, label = { Text(stringResource(R.string.lending_due_date)) }, placeholder = { Text(stringResource(R.string.wealth_date_format)) }, singleLine = true, enabled = !state.busy, isError = form.dueDate.isNotBlank() && form.date == null)
                OutlinedTextField(form.remark, { onAction(LendingAction.EditorChanged(form.copy(remark = it))) }, label = { Text(stringResource(R.string.lending_remark)) }, singleLine = true, enabled = !state.busy)
                if (state.writeFailed) Text(state.errorKey ?: stringResource(R.string.lending_write_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(LendingAction.Save) }, enabled = form.valid && !state.busy) },
        dismissButton = { TextButton(onClick = { onAction(LendingAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun SettlementDialog(form: LendingSettlement, state: LendingUiState, onAction: (LendingAction) -> Unit) {
    val progress = state.lendings.firstOrNull { it.record.id == form.lendingId }
    val amount = form.money
    val valid = amount != null && form.cashAccountId.isNotBlank() && (form.interest || amount <= (progress?.outstanding ?: amount))
    val confirmTitle = stringResource(if (form.interest) R.string.lending_interest else R.string.lending_settle)
    AlertDialog(
        onDismissRequest = { if (!state.busy) onAction(LendingAction.Dismiss) },
        title = { Text(stringResource(if (form.interest) R.string.lending_interest_title else R.string.lending_settle_title, progress?.record?.counterparty.orEmpty())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                Text(stringResource(R.string.lending_cash_account))
                AccountPicker(state.availableAccounts.map { it.id to it.name }, form.cashAccountId, !state.busy) { onAction(LendingAction.SettlementChanged(form.copy(cashAccountId = it))) }
                OutlinedTextField(
                    form.amount,
                    { onAction(LendingAction.SettlementChanged(form.copy(amount = it))) },
                    label = { Text(stringResource(if (form.interest) R.string.lending_interest_amount else R.string.lending_settle_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !state.busy,
                )
                progress?.let { Text(stringResource(R.string.lending_outstanding, it.outstanding.toDisplayString()), style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.lending_settle_hint), style = MaterialTheme.typography.bodySmall)
                if (state.writeFailed) Text(state.errorKey ?: stringResource(R.string.lending_write_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(LendingAction.ConfirmSettlement(confirmTitle)) }, enabled = valid && !state.busy) },
        dismissButton = { TextButton(onClick = { onAction(LendingAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun AccountPicker(options: List<Pair<String, String>>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.wealth_choose_account))
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(id) }) }
        }
    }
}

@Composable
private fun directionLabel(direction: LendingDirection): String =
    stringResource(if (direction.isLend) R.string.lending_direction_lend else R.string.lending_direction_borrow)

@Preview(showBackground = true)
@Composable
private fun LendingPreview() {
    VipLedgerTheme { LendingContent(LendingUiState(loading = false), {}, {}, { _, _ -> }) }
}
