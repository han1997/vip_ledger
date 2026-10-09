package com.han1997.vipledger.feature.ledger.ui.txnedit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.PrimaryButton
import com.han1997.vipledger.core.designsystem.component.SecondaryButton
import com.han1997.vipledger.core.designsystem.component.VipLedgerConfirmDialog
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.accountTypeLabel
import com.han1997.vipledger.core.designsystem.format.fromPickerUtcMillis
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.format.timeLabelWithSeconds
import com.han1997.vipledger.core.designsystem.format.toPickerUtcMillis
import com.han1997.vipledger.core.designsystem.format.transactionTypeLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.core.model.time.toLocalDateTime
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** 一级分类网格列数：优先让名称完整显示，4 列在窄屏也放得下常见分类名。 */
private const val CATEGORY_COLUMNS = 4

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
    var showTimePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
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
    // 今天/昨天按真实日期实时计算：进入表单时刷新一次，跨零点后 chip 口径不过期。
    LaunchedEffect(Unit) { onAction(TxnEditAction.RefreshToday) }
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

    // 未保存退出确认：顶栏返回与系统返回都走这里。
    val requestExit: () -> Unit = {
        if (state.hasUnsavedChanges) showDiscardDialog = true else onBack()
    }
    BackHandler(onBack = requestExit)

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(
                    if (state.isEditing) R.string.txn_edit_title_edit else R.string.txn_edit_title_new,
                ),
                onBack = requestExit,
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
        } else if (state.isAiMode) {
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
                        enabled = !state.isBusy,
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { TypeSection(state, onAction) }
                item { AmountSection(state, onAction, amountFocusRequester) }
                item { AccountSection(state, onAction) }
                item { CategorySection(state, onAction) }
                item { TagSection(state, onAction) }
                item {
                    DateSection(
                        state = state,
                        onAction = onAction,
                        onPickDate = { showDatePicker = true },
                        onPickTime = { showTimePicker = true },
                        zone = zone,
                    )
                }
                item { DetailSection(state, onAction) }
                item {
                    PrimaryButton(
                        text = stringResource(R.string.common_save),
                        onClick = { onAction(TxnEditAction.Save) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy && !state.editingUnavailable,
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
                            val date = picked.fromPickerUtcMillis(zone).toLocalDate(zone)
                            onAction(TxnEditAction.SelectDate(date))
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

    if (showTimePicker) {
        TimeInputDialog(
            initial = state.occurredAt.toLocalDateTime(zone).time,
            onConfirm = {
                onAction(TxnEditAction.SelectTime(it))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showDiscardDialog) {
        VipLedgerConfirmDialog(
            title = stringResource(R.string.txn_edit_unsaved_title),
            message = stringResource(R.string.txn_edit_unsaved_message),
            confirmLabel = stringResource(R.string.txn_edit_unsaved_discard),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
            onDismiss = { showDiscardDialog = false },
            danger = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeSection(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    SectionLabel(stringResource(R.string.txn_edit_type))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            TransactionType.EXPENSE,
            TransactionType.REFUND,
            TransactionType.INCOME,
            TransactionType.TRANSFER,
            TransactionType.DEBT_REPAY,
            TransactionType.ADJUST,
        ).forEach { type ->
            FilterChip(
                selected = state.type == type,
                onClick = { onAction(TxnEditAction.SelectType(type)) },
                label = { Text(text = transactionTypeLabel(type)) },
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
private fun AiEntryPreview(state: TxnEditUiState, parsed: ParsedEntry) {
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

/**
 * 一级分类网格：选中父级的二级区直接插在它所在那一行下方，
 * 一次只展开一个父级；「不选分类」作为首格允许清除选择。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategorySection(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    val nodes = state.visibleCategories
    if (nodes.isEmpty()) return

    SectionLabel(stringResource(R.string.txn_edit_category))
    val cells: List<CategoryNode?> = listOf<CategoryNode?>(null) + nodes
    cells.chunked(CATEGORY_COLUMNS).forEach { rowCells ->
        androidx.compose.runtime.key(rowCells.firstOrNull()?.category?.id ?: "no-category") {
            Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowCells.forEach { node ->
                        if (node == null) {
                            CategoryCell(
                                name = stringResource(R.string.txn_edit_category_none),
                                selected = state.categoryId == null,
                                onClick = { onAction(TxnEditAction.SelectCategory(null)) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            CategoryCell(
                                name = node.name,
                                selected = state.categoryParentId == node.category.id,
                                onClick = { onAction(TxnEditAction.ToggleCategoryParent(node.category.id)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(CATEGORY_COLUMNS - rowCells.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                val expandedNode = rowCells.firstOrNull { it?.category?.id == state.expandedCategoryId }
                if (expandedNode != null && expandedNode.children.isNotEmpty()) {
                    CategoryChildrenRow(state, onAction, parent = expandedNode)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChildrenRow(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    parent: CategoryNode,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.categoryId == parent.category.id,
            onClick = { onAction(TxnEditAction.SelectCategory(parent.category.id)) },
            label = { Text(text = stringResource(R.string.txn_edit_category_clear)) },
        )
        parent.children.forEach { child ->
            FilterChip(
                selected = state.categoryId == child.id,
                onClick = { onAction(TxnEditAction.SelectCategory(child.id)) },
                label = { Text(text = child.name) },
            )
        }
    }
}

@Composable
private fun CategoryCell(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    SectionLabel(stringResource(R.string.txn_edit_tags))
    val visibleTags = if (state.tagInput.isBlank()) state.tags else state.matchingTags
    if (visibleTags.isEmpty()) {
        Text(
            text = stringResource(R.string.txn_edit_tags_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleTags.forEach { tag ->
                FilterChip(
                    selected = tag.id in state.selectedTagIds,
                    onClick = { onAction(TxnEditAction.ToggleTag(tag.id)) },
                    label = { Text(text = tag.name) },
                )
            }
        }
    }

    TextButton(onClick = { onAction(TxnEditAction.ToggleTagEditor) }) {
        Text(
            text = stringResource(
                if (state.tagEditorExpanded) R.string.txn_edit_tag_collapse else R.string.txn_edit_tag_add,
            ),
        )
    }
    AnimatedVisibility(visible = state.tagEditorExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledTextField(
                label = stringResource(R.string.txn_edit_tags),
                value = state.tagInput,
                onValueChange = { onAction(TxnEditAction.UpdateTagInput(it)) },
                placeholder = stringResource(R.string.txn_edit_tag_search_placeholder),
            )
            when {
                state.tagInput.isBlank() -> Unit
                state.existingTag != null -> Text(
                    text = stringResource(R.string.txn_edit_tag_exists),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> PrimaryButton(
                    text = stringResource(R.string.txn_edit_tag_create, state.tagInput.trim()),
                    onClick = { onAction(TxnEditAction.CreateTag) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCreatingTag,
                )
            }
            if (state.tagCreateFailed) {
                Text(
                    text = stringResource(R.string.txn_edit_tag_create_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateSection(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    zone: TimeZone,
) {
    SectionLabel(stringResource(R.string.txn_edit_date))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.occurredAt >= state.todayMillis,
            onClick = { onAction(TxnEditAction.SelectRelativeDate(0)) },
            label = { Text(text = stringResource(R.string.txn_edit_date_quick_today)) },
        )
        FilterChip(
            selected = state.occurredAt in state.yesterdayMillis until state.todayMillis,
            onClick = { onAction(TxnEditAction.SelectRelativeDate(1)) },
            label = { Text(text = stringResource(R.string.txn_edit_date_quick_yesterday)) },
        )
        FilterChip(
            selected = false,
            onClick = onPickDate,
            label = { Text(text = fullDateLabel(state.occurredAt.toLocalDate(zone))) },
        )
    }

    SectionLabel(stringResource(R.string.txn_edit_time))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = false,
            onClick = onPickTime,
            label = { Text(text = timeLabelWithSeconds(state.occurredAt, zone)) },
        )
        FilterChip(
            selected = false,
            onClick = { onAction(TxnEditAction.SelectNow) },
            label = { Text(text = stringResource(R.string.txn_edit_time_now)) },
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
    if (state.type.isAccountingForStats) {
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

/** 秒级时间输入：三个数字框，全部合法才能确定。 */
@Composable
private fun TimeInputDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var hour by remember { mutableStateOf(pad2(initial.hour)) }
    var minute by remember { mutableStateOf(pad2(initial.minute)) }
    var second by remember { mutableStateOf(pad2(initial.second)) }
    val parsed = parseEntryClock(hour, minute, second)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.txn_edit_time_pick)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField(
                    label = stringResource(R.string.txn_edit_time_hour),
                    value = hour,
                    onValueChange = { hour = it },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = stringResource(R.string.txn_edit_time_minute),
                    value = minute,
                    onValueChange = { minute = it },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = stringResource(R.string.txn_edit_time_second),
                    value = second,
                    onValueChange = { second = it },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null,
            ) {
                Text(text = stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LabeledTextField(
        label = label,
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(2)) },
        keyboardType = KeyboardType.Number,
        modifier = modifier,
    )
}

private fun pad2(value: Int): String = value.toString().padStart(2, '0')

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
                expandedCategoryId = "c1",
            ),
            onAction = {},
            onBack = {},
        )
    }
}
