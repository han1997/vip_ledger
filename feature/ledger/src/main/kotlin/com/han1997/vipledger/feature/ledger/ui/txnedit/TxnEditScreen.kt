package com.han1997.vipledger.feature.ledger.ui.txnedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.PrimaryButton
import com.han1997.vipledger.core.designsystem.component.SecondaryButton
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.accountTypeLabel
import com.han1997.vipledger.core.designsystem.format.fromPickerUtcMillis
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.format.toPickerUtcMillis
import com.han1997.vipledger.core.designsystem.format.transactionTypeLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.TimeZone

/** 记账编辑：支出 / 收入 / 转账 / 还款 / 调整共用一个表单。 */
@Composable
fun TxnEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TxnEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TxnEditContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TxnEditContent(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 日期选择器只认「UTC 零点」，设备时区的换算放在这里做（纯展示层互操作）。
    val zone = remember { TimeZone.currentSystemDefault() }
    val snackbarHostState = remember { SnackbarHostState() }
    val amountFocusRequester = remember { FocusRequester() }
    val aiInputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showDatePicker by remember { mutableStateOf(false) }
    val invalidAmountMessage = stringResource(R.string.txn_edit_amount_invalid)
    val failedMessage = stringResource(R.string.common_error_unknown)
    val aiNoEntryMessage = stringResource(R.string.txn_edit_ai_no_entry)
    val aiMultipleEntriesMessage = stringResource(R.string.txn_edit_ai_multiple_entries)

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }
    LaunchedEffect(state.aiParsed) {
        if (state.aiParsed != null) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(state.isLoading, state.isEditing, state.isAiMode) {
        if (!state.isLoading && !state.isEditing && state.isAiMode) {
            aiInputFocusRequester.requestFocus()
        } else if (!state.isLoading && !state.isEditing && !state.isAiMode) {
            amountFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(state.aiError) {
        when (state.aiError) {
            TxnEditError.AI_PARSE_FAILED -> snackbarHostState.showSnackbar(aiNoEntryMessage)
            TxnEditError.AI_MULTIPLE_ENTRIES -> snackbarHostState.showSnackbar(aiMultipleEntriesMessage)
            else -> Unit
        }
    }
    LaunchedEffect(state.error) {
        when (state.error) {
            TxnEditError.INVALID_AMOUNT -> snackbarHostState.showSnackbar(invalidAmountMessage)
            TxnEditError.SAVE_FAILED -> snackbarHostState.showSnackbar(state.saveFailureMessage ?: failedMessage)
            TxnEditError.AI_PARSE_FAILED -> snackbarHostState.showSnackbar(aiNoEntryMessage)
            TxnEditError.AI_MULTIPLE_ENTRIES -> snackbarHostState.showSnackbar(aiMultipleEntriesMessage)
            null -> Unit
        }
        if (state.error != null) onAction(TxnEditAction.ConsumeError)
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(
                    if (state.isEditing) R.string.txn_edit_title_edit else R.string.txn_edit_title_new,
                ),
                onBack = onBack,
                actions = {
                    if (!state.isAiMode) {
                        TextButton(
                            onClick = { onAction(TxnEditAction.Save) },
                            enabled = !state.isSaving,
                        ) {
                            Text(text = stringResource(R.string.common_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState()
            }
        } else {
            if (state.isAiMode) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    LabeledTextField(
                        label = stringResource(R.string.txn_edit_ai_input),
                        value = state.aiInput,
                        onValueChange = { onAction(TxnEditAction.UpdateAiInput(it)) },
                        placeholder = stringResource(R.string.txn_edit_ai_input_placeholder),
                        singleLine = false,
                        textFieldModifier = Modifier
                            .focusRequester(aiInputFocusRequester)
                            .heightIn(min = 180.dp),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.txn_edit_ai_parse),
                        onClick = { onAction(TxnEditAction.ParseAi) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.aiParsing,
                    )
                    if (state.aiParsing) LoadingState()
                    if (state.aiParsed != null) {
                        AiEntryPreview(state, state.aiParsed)
                        PrimaryButton(
                            text = stringResource(R.string.txn_edit_ai_confirm),
                            onClick = { onAction(TxnEditAction.ConfirmAiEntry) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving,
                        )
                        SecondaryButton(
                            text = stringResource(R.string.txn_edit_ai_apply),
                            onClick = { onAction(TxnEditAction.ApplyAiEntry(state.aiParsed)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SecondaryButton(
                        text = stringResource(R.string.txn_edit_ai_exit),
                        onClick = { onAction(TxnEditAction.ExitAiMode) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    TypeSection(state, onAction)
                    AmountSection(state, onAction, amountFocusRequester)
                    AccountSection(state, onAction)
                    CategorySection(state, onAction)
                    TagSection(state, onAction)
                    DateSection(
                        state = state,
                        onAction = onAction,
                        onPickDate = { showDatePicker = true },
                    )
                    DetailSection(state, onAction)
                    PrimaryButton(
                        text = stringResource(R.string.common_save),
                        onClick = { onAction(TxnEditAction.Save) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.occurredAt.toPickerUtcMillis(zone),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            onAction(TxnEditAction.SelectDate(picked.fromPickerUtcMillis(zone)))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(text = stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeSection(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    SectionLabel(stringResource(R.string.txn_edit_type))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            TransactionType.EXPENSE,
            TransactionType.INCOME,
            TransactionType.TRANSFER,
            TransactionType.DEBT_REPAY,
            TransactionType.ADJUST,
        ).forEach { type ->
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = state.type == type,
                onClick = { onAction(TxnEditAction.SelectType(type)) },
                label = {
                    Text(
                        text = transactionTypeLabel(type),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun AmountSection(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    focusRequester: FocusRequester,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { onAction(TxnEditAction.EnterAiMode) }) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.txn_edit_ai_input),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        LabeledTextField(
            label = stringResource(R.string.txn_edit_amount),
            value = state.amountInput,
            onValueChange = { onAction(TxnEditAction.UpdateAmount(it)) },
            placeholder = stringResource(R.string.txn_edit_amount_placeholder),
            keyboardType = KeyboardType.Decimal,
            isError = state.amountError,
            supportingText = if (state.amountError) stringResource(R.string.txn_edit_amount_invalid) else null,
            modifier = Modifier.weight(1f),
            textFieldModifier = Modifier.focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun AiEntryPreview(state: TxnEditUiState, parsed: com.han1997.vipledger.core.domain.nlp.ParsedEntry) {
    val account = parsed.accountId?.let { id -> state.accounts.firstOrNull { it.id == id }?.name }
        ?: state.accountId?.let { id -> state.accounts.firstOrNull { it.id == id }?.name }
        ?: stringResource(R.string.txn_edit_account_none)
    val category = parsed.categoryId?.let { id ->
        state.categoryNodes.firstNotNullOfOrNull { node ->
            (listOf(node.category) + node.children).firstOrNull { it.id == id }?.name
        }
    } ?: stringResource(R.string.txn_edit_category_none)
    val targetAccount = parsed.toAccountId?.let { id -> state.accounts.firstOrNull { it.id == id }?.name }
    GroupCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(R.string.txn_edit_ai_preview), style = MaterialTheme.typography.titleMedium)
            Text(text = transactionTypeLabel(parsed.type), style = MaterialTheme.typography.bodyMedium)
            AmountText(amount = parsed.amount, style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(R.string.txn_edit_ai_preview_account, account))
            Text(text = stringResource(R.string.txn_edit_ai_preview_category, category))
            if (targetAccount != null) {
                Text(text = stringResource(R.string.txn_edit_ai_preview_target, targetAccount))
            }
            Text(text = stringResource(R.string.txn_edit_ai_preview_date, fullDateLabel(parsed.date)))
            if (parsed.unresolved.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.txn_edit_ai_correction_hint),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountSection(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    SectionLabel(stringResource(R.string.txn_edit_account))
    if (state.accounts.isEmpty()) {
        Text(
            text = stringResource(R.string.account_manage_add),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.accounts.forEach { account ->
                AccountChip(
                    account = account,
                    selected = state.accountId == account.id,
                    onClick = { onAction(TxnEditAction.SelectAccount(account.id)) },
                )
            }
        }
    }

    if (!state.type.requiresTargetAccount) return

    val label = if (state.type == TransactionType.DEBT_REPAY) {
        stringResource(R.string.txn_edit_account_target)
    } else {
        stringResource(R.string.txn_edit_account_to)
    }
    SectionLabel(label)
    val candidates = state.targetCandidates
    if (candidates.isEmpty()) {
        Text(
            text = stringResource(R.string.txn_edit_account_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            candidates.forEach { account ->
                AccountChip(
                    account = account,
                    selected = state.toAccountId == account.id,
                    onClick = { onAction(TxnEditAction.SelectTargetAccount(account.id)) },
                )
            }
        }
    }
}

@Composable
private fun AccountChip(account: Account, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = account.name + " · " + accountTypeLabel(account.type)) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    val nodes = state.categoryNodes.filter { it.category.kind == state.categoryKind }
    if (nodes.isEmpty()) return

    SectionLabel(stringResource(R.string.txn_edit_category))
    val selectedParent = nodes.firstOrNull { node ->
        node.category.id == state.categoryId || node.children.any { it.id == state.categoryId }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { node ->
            FilterChip(
                selected = selectedParent?.category?.id == node.category.id,
                onClick = {
                    onAction(
                        TxnEditAction.SelectCategory(
                            if (state.categoryId == node.category.id) null else node.category.id,
                        ),
                    )
                },
                label = { Text(text = node.name) },
            )
        }
    }

    val children = selectedParent?.children.orEmpty()
    AnimatedVisibility(visible = children.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.txn_edit_category_second))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                children.forEach { child ->
                    FilterChip(
                        selected = state.categoryId == child.id,
                        onClick = { onAction(TxnEditAction.SelectCategory(child.id)) },
                        label = { Text(text = child.name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    SectionLabel(stringResource(R.string.txn_edit_tags))
    if (state.tags.isEmpty()) {
        Text(
            text = stringResource(R.string.txn_edit_tags_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.tags.forEach { tag ->
            FilterChip(
                selected = tag.id in state.selectedTagIds,
                onClick = { onAction(TxnEditAction.ToggleTag(tag.id)) },
                label = { Text(text = tag.name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateSection(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    onPickDate: () -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    SectionLabel(stringResource(R.string.txn_edit_date))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.occurredAt >= state.todayMillis,
            onClick = { onAction(TxnEditAction.SelectDate(state.todayMillis)) },
            label = { Text(text = stringResource(R.string.txn_edit_date_quick_today)) },
        )
        FilterChip(
            selected = state.occurredAt in state.yesterdayMillis until state.todayMillis,
            onClick = { onAction(TxnEditAction.SelectDate(state.yesterdayMillis)) },
            label = { Text(text = stringResource(R.string.txn_edit_date_quick_yesterday)) },
        )
        FilterChip(
            selected = false,
            onClick = onPickDate,
            label = { Text(text = fullDateLabel(state.occurredAt.toLocalDate(zone))) },
        )
    }
}

@Composable
private fun DetailSection(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    LabeledTextField(
        label = stringResource(R.string.txn_edit_field_title),
        value = state.title,
        onValueChange = { onAction(TxnEditAction.UpdateTitle(it)) },
        placeholder = stringResource(R.string.txn_edit_field_title_placeholder),
    )
    LabeledTextField(
        label = stringResource(R.string.txn_edit_field_remark),
        value = state.remark,
        onValueChange = { onAction(TxnEditAction.UpdateRemark(it)) },
        singleLine = false,
    )
    if (state.type == TransactionType.EXPENSE || state.type == TransactionType.INCOME) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.txn_edit_exclude_stats),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = state.excludeFromStats,
                onCheckedChange = { onAction(TxnEditAction.UpdateExcludeFromStats(it)) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Preview(showBackground = true)
@Composable
private fun TxnEditContentPreview() {
    VipLedgerTheme {
        TxnEditContent(
            state = TxnEditUiState(
                isLoading = false,
                ledgerId = "l1",
                type = TransactionType.EXPENSE,
                amountInput = "38.00",
                title = "午餐",
                accounts = listOf(
                    Account(
                        id = "a1",
                        ledgerId = "l1",
                        name = "现金",
                        type = AccountType.CASH,
                        createdAt = 0,
                        updatedAt = 0,
                    ),
                ),
                accountId = "a1",
                categoryNodes = listOf(
                    CategoryNode(
                        category = Category(
                            id = "c1",
                            ledgerId = "l1",
                            name = "餐饮",
                            kind = CategoryKind.EXPENSE,
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                        children = listOf(
                            Category(
                                id = "c2",
                                ledgerId = "l1",
                                parentId = "c1",
                                name = "午餐",
                                kind = CategoryKind.EXPENSE,
                                createdAt = 0,
                                updatedAt = 0,
                            ),
                        ),
                    ),
                ),
                categoryId = "c2",
            ),
            onAction = {},
            onBack = {},
        )
    }
}
