package com.han1997.vipledger.feature.ledger.ui.autobook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
        LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(VipLedgerTokens.Space4),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            item {
                GroupCard {
                Toggle(stringResource(R.string.auto_enable), config.enabled, state.hasUsableLedger && !state.busy) {
                    onAction(AutoBookAction.Config(config.copy(enabled = it)))
                }
                HorizontalDivider()
                Choice(stringResource(R.string.auto_ledger), config.ledgerId, state.ledgers.map { it.id to it.name }, !state.busy) {
                    onAction(AutoBookAction.Config(config.copy(ledgerId = it, enabled = false, verifiedApps = emptySet())))
                }
                if (!state.hasUsableLedger) Hint(stringResource(if (state.ledgers.isEmpty()) R.string.auto_no_ledgers else R.string.auto_select_ledger_first))
                }
            }
            item {
                ExpandableSection(stringResource(R.string.auto_permissions_title),
                    stringResource(R.string.auto_permissions_summary,
                        listOf(state.permissions.notifications, state.permissions.accessibility).count { it },
                        listOf(state.runtime.notificationConnected, state.runtime.accessibilityConnected).count { it })) {
                    Hint(stringResource(R.string.auto_permission_state, statusLabel(state.permissions.notifications), statusLabel(state.permissions.accessibility)))
                    Hint(stringResource(R.string.auto_service_state, statusLabel(state.runtime.notificationConnected), statusLabel(state.runtime.accessibilityConnected)))
                    CaptureSetting.entries.forEach { setting ->
                        ActionRow(title = stringResource(when (setting) {
                            CaptureSetting.NOTIFICATIONS -> R.string.auto_notification_permission
                            CaptureSetting.ACCESSIBILITY -> R.string.auto_accessibility_permission
                            CaptureSetting.APP_DETAILS -> R.string.auto_restricted_settings
                            CaptureSetting.BATTERY -> R.string.auto_battery
                        }), onClick = { onAction(AutoBookAction.OpenSetting(setting)) })
                    }
                    Toggle(stringResource(R.string.auto_ocr), config.ocrEnabled, !state.busy) { onAction(AutoBookAction.Config(config.copy(ocrEnabled = it))) }
                    HelpText(stringResource(R.string.auto_settings_help), stringResource(R.string.auto_oem_help))
                    state.runtime.lastEventAt?.let { Hint(stringResource(R.string.auto_last_event, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))) }
                    state.runtime.lastReadAt?.let { Hint(stringResource(R.string.auto_last_read, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))) }
                    state.runtime.lastRecordAt?.let { Hint(stringResource(R.string.auto_last_record, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))) }
                    state.runtime.lastCaptureOutcome?.let { outcome ->
                        Hint(stringResource(when (outcome) {
                            CaptureOutcome.PROCESSING -> R.string.auto_capture_processing
                            CaptureOutcome.UPDATED -> R.string.auto_capture_updated
                            CaptureOutcome.UNCHANGED -> R.string.auto_capture_unchanged
                            CaptureOutcome.FAILED -> R.string.auto_capture_failed
                        }))
                    }
                    state.runtime.lastAmountRecognized?.let { recognized ->
                        Hint(stringResource(if (recognized) R.string.auto_amount_recognized else R.string.auto_amount_missing))
                    }
                    if (state.runtime.issue != null) Hint(stringResource(R.string.auto_runtime_issue))
                }
            }
            item {
                ExpandableSection(stringResource(R.string.auto_apps), stringResource(R.string.auto_apps_summary, config.enabledApps.size)) {
                    Hint(stringResource(R.string.auto_apps_hint))
                    PaymentApp.entries.forEachIndexed { index, app ->
                    if (index > 0) HorizontalDivider()
                    Toggle(appLabel(app), app in config.enabledApps, !state.busy) {
                        onAction(AutoBookAction.Config(config.copy(enabledApps = if (it) config.enabledApps + app else config.enabledApps - app)))
                    }
                    Toggle(stringResource(R.string.auto_verified_short), app in config.verifiedApps, !state.busy && app in config.enabledApps) {
                        onAction(AutoBookAction.Config(config.copy(verifiedApps = if (it) config.verifiedApps + app else config.verifiedApps - app)))
                    }
                    }
                    HelpText(stringResource(R.string.auto_validation_title), stringResource(R.string.auto_validation_help))
                }
            }
            item {
                ExpandableSection(stringResource(R.string.auto_bindings_short), stringResource(R.string.auto_bindings_summary, state.bindings.size)) {
                Hint(stringResource(R.string.auto_binding_optional))
                state.bindings.forEach { binding ->
                    ActionRow(title = binding.token,
                        subtitle = stringResource(R.string.auto_binding_row, appLabel(binding.app), binding.token,
                            state.accounts.find { it.id == binding.accountId }?.name ?: stringResource(R.string.auto_unavailable_account)),
                        trailingText = stringResource(R.string.auto_remove), enabled = !state.busy,
                        onClick = { onAction(AutoBookAction.Unbind(binding)) })
                }
                HorizontalDivider()
                Choice(stringResource(R.string.auto_app), state.bindingApp.name, PaymentApp.entries.map { it.name to appLabel(it) }, !state.busy) {
                    onAction(AutoBookAction.BindingApp(PaymentApp.valueOf(it)))
                }
                if (state.bindingTokens.isNotEmpty()) Choice(stringResource(R.string.auto_pick_token), state.bindingToken,
                    state.bindingTokens.map { it to it }, !state.busy) { onAction(AutoBookAction.BindingToken(it)) }
                OutlinedTextField(state.bindingToken, { onAction(AutoBookAction.BindingToken(it)) },
                    label = { Text(stringResource(R.string.auto_token_short)) },
                    placeholder = { Text(stringResource(R.string.auto_token_example)) },
                    singleLine = true, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = VipLedgerTokens.Space4))
                HelpText(stringResource(R.string.auto_binding_how), stringResource(R.string.auto_binding_help))
                Choice(stringResource(R.string.auto_account), state.bindingAccount, state.accounts.map { it.id to it.name }, !state.busy) { onAction(AutoBookAction.BindingAccount(it)) }
                if (!state.hasUsableLedger || state.accounts.isEmpty()) Hint(stringResource(
                    if (!state.hasUsableLedger) R.string.auto_select_ledger_first else R.string.auto_no_accounts))
                Box(Modifier.padding(VipLedgerTokens.Space4)) {
                PrimaryButton(text = stringResource(R.string.auto_bind), onClick = { onAction(AutoBookAction.Bind) },
                    enabled = state.canBind)
                }
                }
            }
            if (state.failed) item {
                Text(stringResource(R.string.auto_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onAction(AutoBookAction.DismissError) }) { Text(stringResource(R.string.auto_dismiss)) }
            }
            item { SectionHeader(title = stringResource(R.string.auto_records)) }
            // 状态筛选（P6）：默认候选；已入账/已忽略可达，已忽略可重新确认。
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.recordFilter == RecordFilter.CANDIDATES,
                        onClick = { onAction(AutoBookAction.SetRecordFilter(RecordFilter.CANDIDATES)) },
                        label = { Text(stringResource(R.string.auto_filter_candidates, state.pendingCount)) },
                    )
                    FilterChip(
                        selected = state.recordFilter == RecordFilter.POSTED,
                        onClick = { onAction(AutoBookAction.SetRecordFilter(RecordFilter.POSTED)) },
                        label = { Text(stringResource(R.string.auto_filter_posted, state.postedCount)) },
                    )
                    FilterChip(
                        selected = state.recordFilter == RecordFilter.IGNORED,
                        onClick = { onAction(AutoBookAction.SetRecordFilter(RecordFilter.IGNORED)) },
                        label = { Text(stringResource(R.string.auto_filter_ignored, state.ignoredCount)) },
                    )
                }
            }
            val shown = state.filteredCandidates()
            if (shown.isEmpty()) {
                item { Hint(stringResource(recordEmptyRes(state.recordFilter, config.enabled))) }
            }
            items(shown, key = { it.id }) { candidate ->
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
                    if (candidate.status == CandidateStatus.PENDING) Text(stringResource(reasonLabel(candidate.reason)))
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
                        if (candidate.status == CandidateStatus.IGNORED) {
                            TextButton(onClick = { onAction(AutoBookAction.Reopen(candidate.id)) }, enabled = !state.busy) {
                                Text(stringResource(R.string.auto_reopen))
                            }
                        }
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

/** 筛选视图的空态文案：候选区分开关提示，已入账/已忽略用各自文案。 */
private fun recordEmptyRes(filter: RecordFilter, enabled: Boolean): Int = when (filter) {
    RecordFilter.CANDIDATES -> if (enabled) R.string.auto_empty_enabled else R.string.auto_empty_disabled
    RecordFilter.POSTED -> R.string.auto_empty_posted
    RecordFilter.IGNORED -> R.string.auto_empty_ignored
}
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
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = VipLedgerTokens.Space4, vertical = VipLedgerTokens.Space2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
@Composable private fun Choice(label: String, selected: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionRow(title = label, trailingText = options.find { it.first == selected }?.second ?: stringResource(R.string.auto_choose),
            onClick = { expanded = true }, enabled = enabled && options.isNotEmpty())
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) }) }
        }
    }
}
@Composable private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = VipLedgerTokens.Space4, vertical = VipLedgerTokens.Space2),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun HelpText(title: String, text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = VipLedgerTokens.Space2)) {
        Text(stringResource(if (expanded) R.string.auto_help_hide else R.string.auto_help_show, title))
    }
    if (expanded) Hint(text)
}
@Composable private fun ExpandableSection(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(if (expanded) R.string.auto_expanded else R.string.auto_collapsed)
    GroupCard {
        ActionRow(title = title, subtitle = summary, modifier = Modifier.semantics { stateDescription = description },
            trailingIcon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            onClick = { expanded = !expanded })
        if (expanded) { HorizontalDivider(); content() }
    }
}
@Preview(showBackground = true)
@Composable private fun PreviewAutoBook() { VipLedgerTheme { AutoBookContent(AutoBookUiState(), {}, {}, { _, _ -> }) } }
