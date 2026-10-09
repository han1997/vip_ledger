package com.han1997.vipledger.feature.ledger.ui.budget

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
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.domain.model.BudgetProgress
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.MonthNavigation

@Composable
fun BudgetScreen(onBack: () -> Unit, viewModel: BudgetViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetContent(state, viewModel::onAction, onBack)
}

@Composable
internal fun BudgetContent(state: BudgetUiState, onAction: (BudgetAction) -> Unit, onBack: () -> Unit) {
    VipLedgerScaffold(topBar = { VipLedgerTopBar(title = stringResource(R.string.ledger_budget_title), onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            item { MonthNavigation(state.month, { onAction(BudgetAction.MoveMonth(-1)) }, { onAction(BudgetAction.MoveMonth(1)) }, { onAction(BudgetAction.Today) }, enabled = !state.saving && state.editor == null) }
            item { Text(stringResource(R.string.ledger_budget_policy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(BudgetAction.Retry) }) }
                else -> {
                    if (state.report?.total == null) item {
                        GroupCard { ActionRow(title = stringResource(R.string.ledger_budget_set_total), onClick = { onAction(BudgetAction.Edit("")) }) }
                    }
                    items(state.report?.progress.orEmpty(), key = { it.limit.scopeKey }) { progress ->
                        GroupCard {
                            ActionRow(title = if (progress.limit.scopeKey.isEmpty()) stringResource(R.string.ledger_budget_total) else state.options.find { it.id == progress.limit.scopeKey }?.name ?: stringResource(R.string.ledger_budget_unknown_category), onClick = { onAction(BudgetAction.Edit(progress.limit.scopeKey)) })
                            BudgetProgressContent(progress, Modifier.padding(horizontal = VipLedgerTokens.Space4).padding(bottom = VipLedgerTokens.Space4))
                        }
                    }
                    item { SecondaryButton(stringResource(R.string.ledger_budget_add_category), { onAction(BudgetAction.Edit(state.options.firstOrNull()?.id ?: "")) }, Modifier.fillMaxWidth(), enabled = state.options.isNotEmpty()) }
                }
            }
        }
    }
    state.editor?.let { form ->
        var expanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!state.saving) onAction(BudgetAction.Dismiss) },
            title = { Text(stringResource(R.string.ledger_budget_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
                    Box {
                        TextButton(onClick = { expanded = true }, enabled = !state.saving) { Text(if (form.scopeKey.isEmpty()) stringResource(R.string.ledger_budget_total) else state.options.find { it.id == form.scopeKey }?.name.orEmpty()) }
                        DropdownMenu(expanded, { expanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.ledger_budget_total)) }, onClick = { expanded = false; onAction(BudgetAction.SelectScope("")) })
                            state.options.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { expanded = false; onAction(BudgetAction.SelectScope(option.id)) }) }
                        }
                    }
                    OutlinedTextField(value = form.input, onValueChange = { onAction(BudgetAction.Input(it)) }, label = { Text(stringResource(R.string.ledger_budget_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.saving, isError = form.input.isNotBlank() && form.amount == null)
                    Text(stringResource(R.string.ledger_budget_edit_hint), style = MaterialTheme.typography.bodySmall)
                    if (state.saveFailed) Text(stringResource(R.string.ledger_save_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onAction(BudgetAction.Disable) }, enabled = !state.saving) { Text(stringResource(R.string.ledger_budget_disable)) }
                }
            },
            confirmButton = { PrimaryButton(stringResource(R.string.common_save), { onAction(BudgetAction.Save) }, enabled = !state.saving && form.amount != null) },
            dismissButton = { TextButton(onClick = { onAction(BudgetAction.Dismiss) }, enabled = !state.saving) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
fun BudgetProgressContent(progress: BudgetProgress, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
        Text(stringResource(R.string.ledger_budget_used, progress.spent.toDisplayString(), requireNotNull(progress.limit.amount).toDisplayString()), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(), color = if (progress.reached(100)) ExpenseRed else MaterialTheme.colorScheme.primary)
        Text(stringResource(if (progress.remaining.cents < 0) R.string.ledger_budget_over else R.string.ledger_budget_remaining, progress.remaining.abs().toDisplayString()), style = MaterialTheme.typography.titleSmall, color = if (progress.remaining.cents < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface)
        if (progress.reached(80)) Text(stringResource(if (progress.reached(100)) R.string.ledger_budget_alert_100 else R.string.ledger_budget_alert_80), style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
    }
}

@Preview(showBackground = true)
@Composable
private fun BudgetPreview() { VipLedgerTheme { BudgetContent(BudgetUiState(loading = false), {}, {}) } }
