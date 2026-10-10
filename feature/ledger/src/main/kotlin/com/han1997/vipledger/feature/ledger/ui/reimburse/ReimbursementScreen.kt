package com.han1997.vipledger.feature.ledger.ui.reimburse

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.foundation.text.KeyboardOptions
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
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.ReimbursementCalculator
import com.han1997.vipledger.core.domain.model.ReimbursementProgress
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R

@Composable
fun ReimbursementScreen(
    onBack: () -> Unit,
    onEditTxn: (String, String) -> Unit,
    viewModel: ReimbursementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReimbursementContent(state, viewModel::onAction, onBack, onEditTxn)
}

@Composable
internal fun ReimbursementContent(
    state: ReimbursementUiState,
    onAction: (ReimbursementAction) -> Unit,
    onBack: () -> Unit,
    onEditTxn: (String, String) -> Unit,
) {
    VipLedgerScaffold(
        topBar = { VipLedgerTopBar(title = stringResource(R.string.reimburse_title), onBack = onBack) },
    ) { padding ->
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
                        onAction = { onAction(ReimbursementAction.Retry) },
                    )
                }
                else -> {
                    item { Text(stringResource(R.string.reimburse_receive_hint), style = MaterialTheme.typography.bodySmall) }
                    if (state.availableAccounts.isEmpty()) {
                        item { ActionRow(title = stringResource(R.string.reimburse_need_account)) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                            PrimaryButton(stringResource(R.string.reimburse_create), { onAction(ReimbursementAction.Create) }, Modifier.weight(1f), enabled = !state.busy)
                            FilterChip(selected = state.showClosed, onClick = { onAction(ReimbursementAction.ToggleClosed) }, label = { Text(stringResource(R.string.reimburse_show_closed)) })
                        }
                    }
                    item { Text(stringResource(R.string.reimburse_pending, state.pendingTotal.toDisplayString()), style = MaterialTheme.typography.titleMedium) }
                    if (state.writeFailed && state.editor == null && state.receipt == null) {
                        item { Text(state.errorKey ?: stringResource(R.string.reimburse_write_failed), color = MaterialTheme.colorScheme.error) }
                    }
                    val visible = state.projects.filter { state.showClosed || !it.project.closed }
                    if (visible.isEmpty()) {
                        item {
                            EmptyState(
                                message = stringResource(R.string.reimburse_empty),
                                actionLabel = stringResource(R.string.reimburse_create),
                                onAction = { onAction(ReimbursementAction.Create) },
                            )
                        }
                    }
                    visible.forEach { progress ->
                        item(key = progress.project.id) { ProjectCard(progress, { onAction(ReimbursementAction.Select(progress.project.id)) }) }
                        if (state.selectedId == progress.project.id) {
                            item(key = "actions-${progress.project.id}") { ProjectActions(progress, state, onAction) }
                            if (progress.items.isNotEmpty()) {
                                item(key = "items-header-${progress.project.id}") { SectionHeader(stringResource(R.string.reimburse_items)) }
                                items(progress.items, key = { "item-${it.id}" }) { txn ->
                                    val remaining = ReimbursementCalculator.remainingFor(txn, progress.receipts)
                                    GroupCard {
                                        ActionRow(
                                            title = txn.title.ifBlank { stringResource(R.string.reimburse_expense_fallback) },
                                            subtitle = stringResource(R.string.reimburse_item_remaining, remaining.toDisplayString()),
                                            trailingText = txn.amount.toDisplayString(),
                                            onClick = { onEditTxn(progress.project.ledgerId, txn.id) },
                                        )
                                    }
                                }
                            }
                            if (progress.receipts.isNotEmpty()) {
                                item(key = "receipts-header-${progress.project.id}") { SectionHeader(stringResource(R.string.reimburse_receipts)) }
                                items(progress.receipts, key = { "receipt-${it.id}" }) { txn ->
                                    GroupCard {
                                        ActionRow(
                                            title = txn.title.ifBlank { stringResource(R.string.reimburse_receipts) },
                                            trailingText = txn.amount.toDisplayString(),
                                            onClick = { onEditTxn(progress.project.ledgerId, txn.id) },
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
    state.receipt?.let { form -> ReceiptDialog(form, state, onAction) }
}

@Composable
private fun ProjectCard(progress: ReimbursementProgress, onClick: () -> Unit) {
    GroupCard {
        ActionRow(
            title = progress.project.title,
            subtitle = listOfNotNull(
                progress.project.counterparty.takeIf { it.isNotBlank() },
                stringResource(
                    R.string.reimburse_progress,
                    progress.received.toDisplayString(),
                    progress.claimable.toDisplayString(),
                    progress.pending.toDisplayString(),
                ),
            ).joinToString(" · "),
            trailingText = when {
                progress.project.closed -> stringResource(R.string.reimburse_closed)
                progress.settled -> stringResource(R.string.reimburse_settled)
                else -> null
            },
            onClick = onClick,
        )
        Column(Modifier.padding(horizontal = VipLedgerTokens.Space4).padding(bottom = VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
            if (progress.claimable.cents > 0) {
                LinearProgressIndicator(
                    progress = { (progress.received.cents.toFloat() / progress.claimable.cents).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (progress.project.remark.isNotBlank()) {
                Text(progress.project.remark, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProjectActions(progress: ReimbursementProgress, state: ReimbursementUiState, onAction: (ReimbursementAction) -> Unit) {
    val pendingItem = progress.items.firstOrNull { ReimbursementCalculator.remainingFor(it, progress.receipts).isPositive }
    val canReceive = !progress.project.closed && pendingItem != null && state.availableAccounts.isNotEmpty() && !state.busy
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
            PrimaryButton(stringResource(R.string.reimburse_receive), { pendingItem?.let { onAction(ReimbursementAction.Receive(progress.project.id, it.id)) } }, Modifier.weight(1f), enabled = canReceive)
            SecondaryButton(stringResource(R.string.reimburse_edit), { onAction(ReimbursementAction.Edit(progress.project.id)) }, Modifier.weight(1f), enabled = !state.busy)
        }
        TextButton(onClick = { onAction(ReimbursementAction.SetClosed(progress.project.id, !progress.project.closed)) }, enabled = !state.busy) {
            Text(stringResource(if (progress.project.closed) R.string.reimburse_restore else R.string.reimburse_terminate))
        }
    }
}

@Composable
private fun EditorDialog(form: ReimbursementEditor, state: ReimbursementUiState, onAction: (ReimbursementAction) -> Unit) {
    val candidates = state.candidatesFor(form.id)
    val selectedAmount = Money.sum(candidates.filter { it.id in form.itemIds }.map { it.amount })
    AlertDialog(
        onDismissRequest = { if (!state.busy) onAction(ReimbursementAction.Dismiss) },
        title = { Text(stringResource(if (form.id == null) R.string.reimburse_create else R.string.reimburse_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                OutlinedTextField(form.title, { onAction(ReimbursementAction.EditorChanged(form.copy(title = it))) }, label = { Text(stringResource(R.string.reimburse_name)) }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(form.counterparty, { onAction(ReimbursementAction.EditorChanged(form.copy(counterparty = it))) }, label = { Text(stringResource(R.string.reimburse_counterparty)) }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(form.remark, { onAction(ReimbursementAction.EditorChanged(form.copy(remark = it))) }, label = { Text(stringResource(R.string.reimburse_remark)) }, singleLine = true, enabled = !state.busy)
                Text(stringResource(R.string.reimburse_pick_items), style = MaterialTheme.typography.titleSmall)
                if (candidates.isEmpty()) Text(stringResource(R.string.reimburse_no_expenses), style = MaterialTheme.typography.bodySmall)
                candidates.take(50).forEach { txn ->
                    ActionRow(
                        title = txn.title.ifBlank { stringResource(R.string.reimburse_expense_fallback) },
                        subtitle = txn.amount.toDisplayString(),
                        trailingText = if (txn.id in form.itemIds) stringResource(R.string.reimburse_selected_item) else null,
                        onClick = { if (!state.busy) onAction(ReimbursementAction.ToggleItem(txn.id)) },
                    )
                }
                Text(stringResource(R.string.reimburse_selected_count, form.itemIds.size, selectedAmount.toDisplayString()), style = MaterialTheme.typography.bodySmall)
                if (state.writeFailed) Text(state.errorKey ?: stringResource(R.string.reimburse_write_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(ReimbursementAction.Save) }, enabled = form.valid && !state.busy) },
        dismissButton = { TextButton(onClick = { onAction(ReimbursementAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ReceiptDialog(form: ReimbursementReceiptForm, state: ReimbursementUiState, onAction: (ReimbursementAction) -> Unit) {
    val progress = state.projects.firstOrNull { it.project.id == form.projectId }
    val items = progress?.items.orEmpty()
    val selectedItem = items.firstOrNull { it.id == form.itemTxnId }
    val remaining = selectedItem?.let { ReimbursementCalculator.remainingFor(it, progress?.receipts.orEmpty()) } ?: Money.ZERO
    val amount = form.money
    val valid = amount != null && form.itemTxnId.isNotBlank() && form.accountId.isNotBlank() && amount <= remaining
    val receiveTitle = stringResource(R.string.reimburse_receive)
    AlertDialog(
        onDismissRequest = { if (!state.busy) onAction(ReimbursementAction.Dismiss) },
        title = { Text(stringResource(R.string.reimburse_receive_title, progress?.project?.title.orEmpty())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                Text(stringResource(R.string.reimburse_receive_item))
                Picker(
                    label = selectedItem?.title?.ifBlank { stringResource(R.string.reimburse_expense_fallback) } ?: stringResource(R.string.reimburse_receive_item),
                    options = items.map { it.id to (it.title.ifBlank { stringResource(R.string.reimburse_expense_fallback) } + " " + it.amount.toDisplayString()) },
                    enabled = !state.busy,
                    onSelect = { id -> onAction(ReimbursementAction.ReceiptChanged(form.copy(itemTxnId = id))) },
                )
                Text(stringResource(R.string.reimburse_receive_account))
                Picker(
                    label = state.availableAccounts.firstOrNull { it.id == form.accountId }?.name ?: stringResource(R.string.wealth_choose_account),
                    options = state.availableAccounts.map { it.id to it.name },
                    enabled = !state.busy,
                    onSelect = { id -> onAction(ReimbursementAction.ReceiptChanged(form.copy(accountId = id))) },
                )
                OutlinedTextField(form.amount, { onAction(ReimbursementAction.ReceiptChanged(form.copy(amount = it))) }, label = { Text(stringResource(R.string.reimburse_receive_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.busy)
                Text(stringResource(R.string.reimburse_item_remaining, remaining.toDisplayString()), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.reimburse_receive_hint), style = MaterialTheme.typography.bodySmall)
                if (state.writeFailed) Text(state.errorKey ?: stringResource(R.string.reimburse_write_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(ReimbursementAction.ConfirmReceipt(receiveTitle)) }, enabled = valid && !state.busy) },
        dismissButton = { TextButton(onClick = { onAction(ReimbursementAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Picker(label: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) { Text(label) }
        DropdownMenu(open, { open = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(id) }) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReimbursementPreview() {
    VipLedgerTheme { ReimbursementContent(ReimbursementUiState(loading = false), {}, {}, { _, _ -> }) }
}
