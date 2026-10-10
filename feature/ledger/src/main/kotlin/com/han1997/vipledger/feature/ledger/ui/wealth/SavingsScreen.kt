package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R

@Composable
fun SavingsScreen(onBack: () -> Unit, onManage: () -> Unit, onEditTxn: (String, String) -> Unit, viewModel: SavingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SavingsContent(state, viewModel::onAction, onBack, onManage, onEditTxn)
}
@Composable
internal fun SavingsContent(state: SavingsUiState, onAction: (SavingsAction) -> Unit, onBack: () -> Unit, onManage: () -> Unit, onEditTxn: (String, String) -> Unit) {
    VipLedgerScaffold(topBar = { VipLedgerTopBar(title = stringResource(R.string.wealth_savings), onBack = onBack, actions = { TextButton(onClick = { onAction(SavingsAction.Create) }, enabled = !state.busy && !state.loading) { Text(stringResource(R.string.wealth_goal_new)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(SavingsAction.Retry) }) }
                else -> {
                    item { Text(stringResource(R.string.wealth_savings_hint), style = MaterialTheme.typography.bodySmall) }
                    if (state.availableAccounts.size < 2) item { ActionRow(title = stringResource(R.string.wealth_need_accounts), onClick = onManage) }
                    item { FilterChip(state.showArchived, { onAction(SavingsAction.ToggleArchived) }, label = { Text(stringResource(R.string.wealth_show_archived)) }) }
                    if (state.writeFailed && state.editor == null && state.movement == null) item { Text(stringResource(R.string.wealth_write_failed), color = MaterialTheme.colorScheme.error) }
                    val goals = state.goals.filter { state.showArchived || !it.goal.archived }
                    if (goals.isEmpty()) item { EmptyState(message = stringResource(R.string.wealth_goals_empty), actionLabel = stringResource(R.string.wealth_goal_new), onAction = { onAction(SavingsAction.Create) }) }
                    goals.forEach { progress ->
                        val goal = progress.goal
                        val account = state.accounts.find { it.id == goal.accountId }
                        item(key = goal.id) {
                            GroupCard {
                                ActionRow(title = goal.name, subtitle = account?.name ?: stringResource(R.string.wealth_unavailable), onClick = { onAction(SavingsAction.Select(goal.id)) })
                                Column(Modifier.padding(horizontal = VipLedgerTokens.Space4).padding(bottom = VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                                    Text(stringResource(R.string.wealth_goal_progress, progress.saved.toDisplayString(), goal.target.toDisplayString()), style = MaterialTheme.typography.titleMedium)
                                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                                    if (goal.archived) Text(stringResource(R.string.wealth_archived))
                                    else if (progress.achieved) Text(stringResource(R.string.wealth_achieved), color = MaterialTheme.colorScheme.primary)
                                    goal.deadline?.let { Text(stringResource(R.string.wealth_deadline_value, it.toString()), style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        if (state.selectedId == goal.id) {
                            item(key = "actions-${goal.id}") {
                                val canMove = !goal.archived && account?.isUsable == true && !account.isDebt && !state.busy
                                Column {
                                    Row(horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                                        PrimaryButton(stringResource(R.string.wealth_deposit), { onAction(SavingsAction.Move(goal.id, true)) }, Modifier.weight(1f), enabled = canMove)
                                        SecondaryButton(stringResource(R.string.wealth_withdraw), { onAction(SavingsAction.Move(goal.id, false)) }, Modifier.weight(1f), enabled = canMove && progress.saved.cents > 0)
                                    }
                                    Row {
                                        TextButton(onClick = { onAction(SavingsAction.Edit(goal.id)) }, enabled = !state.busy) { Text(stringResource(R.string.wealth_goal_edit)) }
                                        TextButton(onClick = { onAction(SavingsAction.Archive(goal.id, !goal.archived)) }, enabled = !state.busy) { Text(stringResource(if (goal.archived) R.string.wealth_restore else R.string.wealth_archive)) }
                                    }
                                    if (account?.isUsable != true || account.isDebt) Text(stringResource(R.string.wealth_unavailable))
                                }
                            }
                            items(progress.txns, key = { "savings-${it.id}" }) { txn -> GroupCard { ActionRow(title = txn.title, subtitle = txn.amount.toDisplayString(), onClick = { onEditTxn(goal.ledgerId, txn.id) }) } }
                        }
                    }
                }
            }
        }
    }
    state.editor?.let { form ->
        AlertDialog(onDismissRequest = { if (!state.busy) onAction(SavingsAction.Dismiss) }, title = { Text(stringResource(if (form.id == null) R.string.wealth_goal_new else R.string.wealth_goal_edit)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                OutlinedTextField(form.name, { onAction(SavingsAction.EditorChanged(form.copy(name = it))) }, label = { Text(stringResource(R.string.wealth_goal_name)) }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(form.target, { onAction(SavingsAction.EditorChanged(form.copy(target = it))) }, label = { Text(stringResource(R.string.wealth_goal_target)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.busy)
                WealthAccountPicker(if (form.accountLocked) state.accounts.filter { it.id == form.accountId } else state.availableAccounts, form.accountId, { onAction(SavingsAction.EditorChanged(form.copy(accountId = it))) }, enabled = !state.busy && !form.accountLocked)
                if (form.accountLocked) Text(stringResource(R.string.wealth_account_locked), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(form.deadline, { onAction(SavingsAction.EditorChanged(form.copy(deadline = it))) }, label = { Text(stringResource(R.string.wealth_deadline)) }, placeholder = { Text(stringResource(R.string.wealth_date_format)) }, singleLine = true, enabled = !state.busy, isError = form.deadline.isNotBlank() && form.date == null)
                if (state.writeFailed) Text(stringResource(R.string.wealth_write_failed), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(SavingsAction.Save) }, enabled = form.valid && !state.busy) }, dismissButton = { TextButton(onClick = { onAction(SavingsAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } })
    }
    state.movement?.let { form ->
        val progress = state.goals.find { it.goal.id == form.goalId }
        val goal = progress?.goal
        val title = stringResource(if (form.deposit) R.string.wealth_deposit_title else R.string.wealth_withdraw_title, goal?.name.orEmpty())
        val amount = Money.parse(form.amount)
        val valid = amount != null && amount.cents > 0 && form.otherAccountId.isNotBlank() && (form.deposit || amount <= (progress?.saved ?: Money.ZERO))
        AlertDialog(onDismissRequest = { if (!state.busy) onAction(SavingsAction.Dismiss) }, title = { Text(title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                Text(stringResource(if (form.deposit) R.string.wealth_from_account else R.string.wealth_to_account))
                WealthAccountPicker(state.availableAccounts.filter { it.id != goal?.accountId }, form.otherAccountId, { onAction(SavingsAction.MovementChanged(form.copy(otherAccountId = it))) }, !state.busy)
                OutlinedTextField(form.amount, { onAction(SavingsAction.MovementChanged(form.copy(amount = it))) }, label = { Text(stringResource(R.string.wealth_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.busy)
                Text(stringResource(R.string.wealth_movement_hint), style = MaterialTheme.typography.bodySmall)
                if (state.writeFailed) Text(stringResource(R.string.wealth_write_failed), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(SavingsAction.ConfirmMovement(title)) }, enabled = valid && !state.busy) }, dismissButton = { TextButton(onClick = { onAction(SavingsAction.Dismiss) }, enabled = !state.busy) { Text(stringResource(R.string.common_cancel)) } })
    }
}

@Composable
private fun WealthAccountPicker(accounts: List<Account>, selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled && accounts.isNotEmpty()) { Text(accounts.find { it.id == selected }?.name ?: stringResource(R.string.wealth_choose_account)) }
        DropdownMenu(open, { open = false }) { accounts.forEach { account -> DropdownMenuItem(text = { Text(account.name) }, onClick = { open = false; onSelect(account.id) }) } }
    }
}
@Preview(showBackground = true)
@Composable
private fun SavingsPreview() { VipLedgerTheme { SavingsContent(SavingsUiState(loading = false), {}, {}, {}, { _, _ -> }) } }
