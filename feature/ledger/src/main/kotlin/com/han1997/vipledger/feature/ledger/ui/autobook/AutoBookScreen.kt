package com.han1997.vipledger.feature.ledger.ui.autobook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.format.transactionTypeLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.autobook.*
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R

@Composable
fun AutoBookScreen(onBack: () -> Unit, onEditTxn: (String, String) -> Unit, viewModel: AutoBookViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(AutoBookAction.Refresh)
        onPauseOrDispose { }
    }
    AutoBookContent(state, viewModel::onAction, onBack, onEditTxn)
}

@Composable
private fun AutoBookContent(state: AutoBookUiState, onAction: (AutoBookAction) -> Unit,
    onBack: () -> Unit, onEditTxn: (String, String) -> Unit) {
    val config = state.config
    VipLedgerScaffold(topBar = { VipLedgerTopBar(title = stringResource(R.string.auto_title), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(VipLedgerTokens.Space4),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            item {
                Text(stringResource(R.string.auto_intro))
                Choice(stringResource(R.string.auto_ledger), config.ledgerId, state.ledgers.map { it.id to it.name }, !state.busy) {
                    onAction(AutoBookAction.Config(config.copy(ledgerId = it, enabled = false, verifiedApps = emptySet())))
                }
                Toggle(stringResource(R.string.auto_enable), config.enabled, config.ledgerId.isNotBlank() && !state.busy) {
                    onAction(AutoBookAction.Config(config.copy(enabled = it)))
                }
                Toggle(stringResource(R.string.auto_ocr), config.ocrEnabled, !state.busy) { onAction(AutoBookAction.Config(config.copy(ocrEnabled = it))) }
            }
            item {
                GroupCard {
                    CaptureSetting.entries.forEach { setting ->
                        ActionRow(title = stringResource(when (setting) {
                            CaptureSetting.NOTIFICATIONS -> R.string.auto_notification_permission
                            CaptureSetting.ACCESSIBILITY -> R.string.auto_accessibility_permission
                            CaptureSetting.APP_DETAILS -> R.string.auto_restricted_settings
                            CaptureSetting.BATTERY -> R.string.auto_battery
                        }), onClick = { onAction(AutoBookAction.OpenSetting(setting)) })
                    }
                }
                Text(stringResource(R.string.auto_oem_help), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.auto_permission_state, statusLabel(state.permissions.notifications), statusLabel(state.permissions.accessibility)))
                Text(stringResource(R.string.auto_service_state, statusLabel(state.runtime.notificationConnected), statusLabel(state.runtime.accessibilityConnected)))
                state.runtime.lastEventAt?.let { Text(stringResource(R.string.auto_last_event, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))) }
                if (state.runtime.issue != null) Text(stringResource(R.string.auto_runtime_issue), color = MaterialTheme.colorScheme.error)
            }
            item { SectionHeader(title = stringResource(R.string.auto_apps)); Text(stringResource(R.string.auto_validation_help)) }
            items(PaymentApp.entries, key = { it.name }) { app ->
                GroupCard {
                    Toggle(appLabel(app), app in config.enabledApps, !state.busy) {
                        onAction(AutoBookAction.Config(config.copy(enabledApps = if (it) config.enabledApps + app else config.enabledApps - app)))
                    }
                    Toggle(stringResource(R.string.auto_verified), app in config.verifiedApps, !state.busy && app in config.enabledApps) {
                        onAction(AutoBookAction.Config(config.copy(verifiedApps = if (it) config.verifiedApps + app else config.verifiedApps - app)))
                    }
                }
            }
            item {
                SectionHeader(title = stringResource(R.string.auto_bindings))
                Text(stringResource(R.string.auto_binding_help))
                Choice(stringResource(R.string.auto_app), state.bindingApp.name, PaymentApp.entries.map { it.name to appLabel(it) }, !state.busy) {
                    onAction(AutoBookAction.BindingApp(PaymentApp.valueOf(it)))
                }
                OutlinedTextField(state.bindingToken, { onAction(AutoBookAction.BindingToken(it)) }, label = { Text(stringResource(R.string.auto_token)) }, modifier = Modifier.fillMaxWidth())
                Choice(stringResource(R.string.auto_account), state.bindingAccount, state.accounts.map { it.id to it.name }, !state.busy) { onAction(AutoBookAction.BindingAccount(it)) }
                PrimaryButton(text = stringResource(R.string.auto_bind), onClick = { onAction(AutoBookAction.Bind) },
                    enabled = !state.busy && state.bindingToken.isNotBlank() && state.bindingAccount.isNotBlank())
            }
            items(state.bindings, key = { "binding:${it.app}:${it.token}" }) { binding ->
                ActionRow(title = stringResource(R.string.auto_binding_row, appLabel(binding.app), binding.token,
                    state.accounts.find { it.id == binding.accountId }?.name ?: stringResource(R.string.auto_unavailable_account)),
                    trailingText = stringResource(R.string.auto_remove), onClick = { onAction(AutoBookAction.Unbind(binding)) })
            }
            if (state.failed) item {
                Text(stringResource(R.string.auto_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onAction(AutoBookAction.DismissError) }) { Text(stringResource(R.string.auto_dismiss)) }
            }
            item { SectionHeader(title = stringResource(R.string.auto_records)) }
            if (state.candidates.isEmpty()) item { Text(stringResource(R.string.auto_empty)) }
            items(state.candidates, key = { it.id }) { candidate ->
                GroupCard {
                    Text(stringResource(R.string.auto_record_row, appLabel(candidate.app), candidate.facts.merchant,
                        candidate.facts.amountCents?.let { Money.ofCents(it).toDisplayString() } ?: stringResource(R.string.auto_unknown)))
                    Text(stringResource(R.string.auto_record_meta, stringResource(when (candidate.source) {
                        CaptureSource.NOTIFICATION -> R.string.auto_source_notification
                        CaptureSource.ACCESSIBILITY -> R.string.auto_source_page
                        CaptureSource.OCR -> R.string.auto_source_image
                    }),
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(candidate.occurredAt))))
                    candidate.facts.accountToken?.let { Text(stringResource(R.string.auto_choice, stringResource(R.string.auto_token), it)) }
                    Text(stringResource(reasonLabel(candidate.reason)))
                    if (candidate.status == CandidateStatus.PENDING) {
                        TextButton(onClick = { onAction(AutoBookAction.Select(candidate)) }, enabled = !state.busy) { Text(stringResource(R.string.auto_review)) }
                        TextButton(onClick = { onAction(AutoBookAction.Ignore(candidate.id)) }, enabled = !state.busy) { Text(stringResource(R.string.auto_ignore)) }
                    } else {
                        Text(stringResource(when (candidate.status) {
                            CandidateStatus.POSTED -> R.string.auto_status_posted
                            CandidateStatus.LINKED -> R.string.auto_status_linked
                            else -> R.string.auto_status_ignored
                        }))
                        candidate.txnId?.let { id -> TextButton(onClick = { onEditTxn(candidate.ledgerId, id) }) { Text(stringResource(R.string.auto_edit_txn)) } }
                    }
                    if (state.form?.id == candidate.id) CandidateEditor(state, onAction)
                }
            }
        }
    }
}

@Composable
private fun CandidateEditor(state: AutoBookUiState, onAction: (AutoBookAction) -> Unit) {
    val form = state.form ?: return
    fun edit(value: CandidateForm) = onAction(AutoBookAction.Edit(value))
    Choice(stringResource(R.string.auto_type), form.type.name,
        listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER, TransactionType.REFUND).map { it.name to transactionTypeLabel(it) }, !state.busy) {
        edit(form.copy(type = TransactionType.valueOf(it), targetId = "", refundId = ""))
    }
    OutlinedTextField(form.amount, { edit(form.copy(amount = it)) }, label = { Text(stringResource(R.string.auto_amount)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(form.title, { edit(form.copy(title = it)) }, label = { Text(stringResource(R.string.auto_merchant)) }, modifier = Modifier.fillMaxWidth())
    Choice(stringResource(R.string.auto_account), form.accountId, state.accounts.map { it.id to it.name }, !state.busy) { edit(form.copy(accountId = it)) }
    if (form.type.requiresTargetAccount) Choice(stringResource(R.string.auto_target), form.targetId, state.accounts.map { it.id to it.name }, !state.busy) { edit(form.copy(targetId = it)) }
    if (form.type == TransactionType.REFUND) Choice(stringResource(R.string.auto_original), form.refundId,
        listOf("" to stringResource(R.string.auto_unlinked_refund)) + state.txns.filter { it.type == TransactionType.EXPENSE && !it.isDraft }.map {
            it.id to stringResource(R.string.auto_txn_choice, it.title, it.amount.toDisplayString())
        }, !state.busy) { edit(form.copy(refundId = it)) }
    PrimaryButton(text = stringResource(R.string.auto_confirm), onClick = { onAction(AutoBookAction.Confirm) }, enabled = !state.busy && form.confirmation() != null)
    Choice(stringResource(R.string.auto_link_existing), form.linkId, state.txns.map {
        it.id to stringResource(R.string.auto_txn_choice, it.title, it.amount.toDisplayString())
    }, !state.busy) { edit(form.copy(linkId = it)) }
    SecondaryButton(text = stringResource(R.string.auto_link), onClick = { onAction(AutoBookAction.Link) }, enabled = !state.busy && form.linkId.isNotBlank())
}
@Composable private fun statusLabel(value: Boolean) = stringResource(if (value) R.string.auto_yes else R.string.auto_no)
@Composable private fun appLabel(value: PaymentApp) = stringResource(when (value) {
    PaymentApp.WECHAT -> R.string.auto_wechat; PaymentApp.ALIPAY -> R.string.auto_alipay
    PaymentApp.JD -> R.string.auto_jd; PaymentApp.PDD -> R.string.auto_pdd; PaymentApp.TAOBAO -> R.string.auto_taobao
})
private fun reasonLabel(value: ReviewReason) = when (value) {
    ReviewReason.NONE -> R.string.auto_reason_none; ReviewReason.UNVERIFIED -> R.string.auto_reason_unverified
    ReviewReason.AMOUNT -> R.string.auto_reason_amount; ReviewReason.ACCOUNT -> R.string.auto_reason_account
    ReviewReason.DIRECTION -> R.string.auto_reason_direction; ReviewReason.REFUND -> R.string.auto_reason_refund
    ReviewReason.DUPLICATE -> R.string.auto_reason_duplicate; ReviewReason.NOT_SUCCESS -> R.string.auto_reason_not_success
    ReviewReason.STORAGE -> R.string.auto_error
    ReviewReason.TIME -> R.string.auto_reason_time
}
@Composable private fun Toggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(vertical = VipLedgerTokens.Space3))
        Switch(checked, onChange, enabled = enabled)
    }
}
@Composable private fun Choice(label: String, selected: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(R.string.auto_choice, label, options.find { it.first == selected }?.second ?: stringResource(R.string.auto_choose)))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) }) }
        }
    }
}
@Preview(showBackground = true)
@Composable private fun PreviewAutoBook() { VipLedgerTheme { AutoBookContent(AutoBookUiState(), {}, {}, { _, _ -> }) } }
