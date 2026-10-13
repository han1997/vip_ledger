package com.han1997.vipledger.feature.ledger.ui.txnedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.han1997.vipledger.core.designsystem.format.monthDayLabel
import com.han1997.vipledger.core.designsystem.format.timeLabel
import com.han1997.vipledger.core.designsystem.format.transactionTypeLabel
import com.han1997.vipledger.core.designsystem.theme.IncomeGreen
import com.han1997.vipledger.core.designsystem.theme.TransferBlue
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.displayEditorAmount
import com.han1997.vipledger.core.designsystem.theme.editorAmountSymbol
import com.han1997.vipledger.core.designsystem.theme.keypadKey
import com.han1997.vipledger.core.designsystem.theme.serifTileInitial
import com.han1997.vipledger.core.designsystem.theme.serifTitle
import com.han1997.vipledger.core.designsystem.theme.vipLedgerAccentColors
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.usecase.wealth.SavingsTxnGuard
import com.han1997.vipledger.core.domain.usecase.reimburse.ReimbursementTxnGuard
import com.han1997.vipledger.core.domain.usecase.lending.LendingTxnGuard
import com.han1997.vipledger.core.domain.usecase.investment.InvestmentTxnGuard
import com.han1997.vipledger.core.domain.usecase.loan.InstallmentTxnGuard
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.core.model.time.toLocalDateTime
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.calendar.calendarWeeks
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 一级分类网格列数:原型 v2.1 为 5 列圆形图标。 */
private const val CATEGORY_COLUMNS = 5

/** 数字键盘行。/ 12 键 + 右侧保存竖条。 */
private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫"),
)

/** 记一笔编辑页(原型 v2.1):页面永不上下滑动,分类区是唯一可内部滚动的区域。 */
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

/** 选择器弹层:账户 / 日期时间 / 标签 / 备注。 */
private enum class AccountSheetSide { SOURCE, TARGET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TxnEditContent(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val snackbarHostState = remember { SnackbarHostState() }
    val aiInputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var accountSheet by remember { mutableStateOf<AccountSheetSide?>(null) }
    var showDateTimeSheet by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    var showRemarkSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val invalidAmountMessage = stringResource(R.string.txn_edit_amount_invalid)
    val failedMessage = stringResource(R.string.common_error_unknown)
    val saveFailureMessage = when (state.saveFailureMessage) {
        SavingsTxnGuard.INVALID_LINK -> stringResource(R.string.wealth_link_invalid)
        SavingsTxnGuard.DIRECTION -> stringResource(R.string.wealth_link_direction)
        SavingsTxnGuard.NEGATIVE -> stringResource(R.string.wealth_link_negative)
        SavingsTxnGuard.OVERFLOW -> stringResource(R.string.wealth_link_overflow)
        ReimbursementTxnGuard.ITEM_LOCKED -> stringResource(R.string.reimburse_item_locked)
        ReimbursementTxnGuard.RECEIPT_LOCKED -> stringResource(R.string.reimburse_receipt_locked)
        LendingTxnGuard.PRINCIPAL_LOCKED -> stringResource(R.string.lending_principal_locked)
        LendingTxnGuard.DIRECTION -> stringResource(R.string.lending_direction_locked)
        LendingTxnGuard.OVER_SETTLE -> stringResource(R.string.lending_over_settle)
        LendingTxnGuard.LINK_INVALID -> stringResource(R.string.lending_link_invalid)
        InstallmentTxnGuard.LINK_INVALID -> stringResource(R.string.loan_link_invalid)
        InstallmentTxnGuard.DIRECTION -> stringResource(R.string.loan_direction_locked)
        InstallmentTxnGuard.AMOUNT_LOCKED -> stringResource(R.string.loan_amount_locked)
        InstallmentTxnGuard.OVER_PAID -> stringResource(R.string.loan_over_paid)
        InvestmentTxnGuard.LINK_INVALID -> stringResource(R.string.investment_link_invalid)
        InvestmentTxnGuard.DIRECTION -> stringResource(R.string.investment_direction_locked)
        InvestmentTxnGuard.OVER_REDEEM -> stringResource(R.string.investment_over_redeem)
        else -> state.saveFailureMessage ?: failedMessage
    }
    val aiNoEntryMessage = stringResource(R.string.txn_edit_ai_no_entry)
    val aiMultipleEntriesMessage = stringResource(R.string.txn_edit_ai_multiple_entries)

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
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
        }
    }
    // 今天/昨天按真实日期实时计算:进入表单时刷新一次,跨零点后 chip 口径不过期。
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
            TxnEditError.SAVE_FAILED -> snackbarHostState.showSnackbar(saveFailureMessage)
            TxnEditError.AI_PARSE_FAILED -> snackbarHostState.showSnackbar(aiNoEntryMessage)
            TxnEditError.AI_MULTIPLE_ENTRIES -> snackbarHostState.showSnackbar(aiMultipleEntriesMessage)
            null -> Unit
        }
        if (state.error != null) onAction(TxnEditAction.ConsumeError)
    }

    // 未保存退出确认:顶栏返回与系统返回都走这里。
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
                actions = {
                    // 编辑态删除(原型 v2.1):危险操作,二次确认。
                    if (state.canDelete) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !state.isBusy) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingState() }

            state.isAiMode -> AiModeColumn(
                state = state,
                onAction = onAction,
                padding = padding,
                aiInputFocusRequester = aiInputFocusRequester,
            )

            else -> EditorColumn(
                state = state,
                onAction = onAction,
                padding = padding,
                onPickAccount = { accountSheet = AccountSheetSide.SOURCE },
                onPickDateTime = { showDateTimeSheet = true },
                onPickTags = { showTagSheet = true },
                onPickRemark = { showRemarkSheet = true },
            )
        }
    }

    accountSheet?.let { side ->
        AccountPickerSheet(
            state = state,
            side = side,
            onAction = onAction,
            onDismiss = { accountSheet = null },
        )
    }
    if (showDateTimeSheet) {
        DateTimePickerSheet(
            state = state,
            zone = zone,
            onAction = onAction,
            onDismiss = { showDateTimeSheet = false },
        )
    }
    if (showTagSheet) {
        TagPickerSheet(state = state, onAction = onAction, onDismiss = { showTagSheet = false })
    }
    if (showRemarkSheet) {
        RemarkSheet(state = state, onAction = onAction, onDismiss = { showRemarkSheet = false })
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
    if (showDeleteDialog) {
        VipLedgerConfirmDialog(
            title = stringResource(R.string.txn_edit_delete_confirm_title),
            message = stringResource(R.string.txn_edit_delete_confirm_message),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                showDeleteDialog = false
                onAction(TxnEditAction.Delete)
            },
            onDismiss = { showDeleteDialog = false },
            danger = true,
        )
    }
}

/**
 * 编辑主体:纵向 flex,**页面永不上下滑动**。
 * 固定区(类型 / 备注金额行 / 胶囊行 / 键盘)+ 唯一可滚动的分类区(weight(1f))。
 */
@Composable
private fun EditorColumn(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    padding: PaddingValues,
    onPickAccount: () -> Unit,
    onPickDateTime: () -> Unit,
    onPickTags: () -> Unit,
    onPickRemark: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = VipLedgerTokens.Space4, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TypeBar(state, onAction)
        CategoryArea(
            state = state,
            onAction = onAction,
            onPickAccount = onPickAccount,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        NoteAmountRow(state, onAction)
        ChipStrip(
            state = state,
            onPickAccount = onPickAccount,
            onPickDateTime = onPickDateTime,
            onPickTags = onPickTags,
            onPickRemark = onPickRemark,
            onEnterAi = { onAction(TxnEditAction.EnterAiMode) },
        )
        KeypadRow(state, onAction)
    }
}

/** 类型分段:原型 .typebar(soft 底 + 发丝边框 + 圆角 13,选中墨绿)。 */
@Composable
private fun TypeBar(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(
                TransactionType.EXPENSE,
                TransactionType.REFUND,
                TransactionType.INCOME,
                TransactionType.TRANSFER,
                TransactionType.DEBT_REPAY,
                TransactionType.ADJUST,
            ).forEach { type ->
                val selected = state.type == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onAction(TxnEditAction.SelectType(type)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = transactionTypeLabel(type),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 分类区:唯一可内部滚动的区域。
 * - 转账 / 还款:两行宽行「转出 / 转入」(原型),下方仍保留分类(不删现有能力);
 * - 其余类型:一级 5 列圆形图标,选中父级的二级面板**插在它所在那一行下方**;
 *   选中一级后若二级面板被裁切,自动滚入视野(`revealSubPanel`)。
 */
/**
 * 分类区:唯一可内部滚动的区域(单个 LazyColumn,不做嵌套滚动)。
 * - 转账 / 还款:两行宽行「转出 / 转入」(原型),下方仍保留分类(不删现有能力);
 * - 其余类型:一级 5 列圆形图标,选中父级的二级面板**插在它所在那一行下方**;
 *   选中一级后若二级面板被裁切,自动滚入视野(`revealSubPanel`)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryArea(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    modifier: Modifier = Modifier,
    onPickAccount: () -> Unit = {},
) {
    val nodes = state.visibleCategories
    val rows = nodes.chunked(CATEGORY_COLUMNS)
    val listState = rememberLazyListState()

    data class GridRow(val key: String, val cells: List<CategoryNode>)
    val gridRows = rows.map { cells -> GridRow(cells.first().category.id, cells) }

    // revealSubPanel:展开的二级面板若不在可视范围内,滚动到它(仅需要时滚动,页面本身不动)。
    LaunchedEffect(state.expandedCategoryId, nodes.size) {
        val expandedId = state.expandedCategoryId ?: return@LaunchedEffect
        val rowIndex = gridRows.indexOfFirst { row -> row.cells.any { it.category.id == expandedId } }
        if (rowIndex < 0) return@LaunchedEffect
        val hasPanel = gridRows[rowIndex].cells
            .firstOrNull { it.category.id == expandedId }?.children.orEmpty().isNotEmpty()
        if (!hasPanel) return@LaunchedEffect
        var target = if (state.type.requiresTargetAccount) 2 else 0
        for (i in 0 until rowIndex) {
            target += 1
            val panel = gridRows[i].cells.firstOrNull { it.category.id == state.expandedCategoryId }
                ?.children.orEmpty().isNotEmpty()
            if (panel) target += 1
        }
        target += 1
        runCatching { listState.animateScrollToItem(target) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        if (state.type.requiresTargetAccount) {
            item(key = "transfer-from") {
                TransferWideRow(
                    label = stringResource(R.string.txn_edit_transfer_from),
                    value = state.accounts.firstOrNull { it.id == state.accountId }?.name
                        ?: stringResource(R.string.txn_edit_pick_account_hint),
                    onClick = onPickAccount,
                )
            }
            item(key = "transfer-to") {
                TransferWideRow(
                    label = stringResource(R.string.txn_edit_transfer_to),
                    value = state.targetCandidates.firstOrNull { it.id == state.toAccountId }?.name
                        ?: stringResource(R.string.txn_edit_pick_account_hint),
                    onClick = onPickAccount,
                )
            }
        }
        gridRows.forEach { row ->
            item(key = row.key) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.cells.forEach { node ->
                        CategoryTile(
                            node = node,
                            selected = state.categoryParentId == node.category.id,
                            onClick = { onAction(TxnEditAction.ToggleCategoryParent(node.category.id)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(CATEGORY_COLUMNS - row.cells.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            val expandedNode = row.cells.firstOrNull { it.category.id == state.expandedCategoryId }
            if (expandedNode != null && expandedNode.children.isNotEmpty()) {
                item(key = "subs-${row.key}") {
                    SubCategoryPanel(
                        parent = expandedNode,
                        selectedId = state.categoryId,
                        onSelect = { onAction(TxnEditAction.SelectCategory(it)) },
                    )
                }
            }
        }
    }
}

/** 一级图标方块(原型 .cat-tile:46px 圆形,选中墨绿渐变)。 */
@Composable
private fun CategoryTile(
    node: CategoryNode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = vipLedgerAccentColors()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(
                        Brush.linearGradient(listOf(accents.heroGradientStart, accents.heroGradientEnd)),
                    )
                    else Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .border(
                            VipLedgerTokens.BorderThin,
                            MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = node.name.take(1),
                style = serifTileInitial,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = node.name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 二级浅底胶囊面板(原型 .sub-panel:soft 底 + 9/10 内边距,4 列小胶囊)。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubCategoryPanel(
    parent: CategoryNode,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 4,
        ) {
            parent.children.forEach { child ->
                val selected = selectedId == child.id
                Surface(
                    modifier = Modifier
                        .widthIn(min = 64.dp)
                        .heightIn(min = VipLedgerTokens.TouchTargetMin)
                        .clickable { onSelect(child.id) },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(
                        VipLedgerTokens.BorderThin,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = child.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 转账宽行(原型 .wide-row:surface + 发丝边框 + 圆角 13)。 */
@Composable
private fun TransferWideRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 备注 + 金额同一行(原型 .meta-row):
 * 备注是**真实输入框**(点按直接弹系统输入法);金额是大点击区(≥48×88dp),
 * 点按收起输入法回到数字键盘。金额按类型着色。
 */
@Composable
private fun NoteAmountRow(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            BasicTextField(
                value = state.title,
                onValueChange = { onAction(TxnEditAction.UpdateTitle(it)) },
                modifier = Modifier.weight(1f).height(44.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                        if (state.title.isEmpty()) {
                            Text(
                                text = stringResource(R.string.txn_edit_note_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            AmountZone(
                state = state,
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
            )
        }
    }
}

/** 金额大点击区(原型 .amt-zone:最小 48×88dp,点按收起输入法)。 */
@Composable
private fun AmountZone(state: TxnEditUiState, onClick: () -> Unit) {
    val hasDigits = state.amountInput.isNotBlank()
    val amountColor = when {
        !hasDigits -> MaterialTheme.colorScheme.onSurfaceVariant
        state.type == TransactionType.INCOME || state.type == TransactionType.REFUND -> IncomeGreen
        state.type == TransactionType.TRANSFER || state.type == TransactionType.DEBT_REPAY -> TransferBlue
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .widthIn(min = 88.dp)
            .heightIn(min = VipLedgerTokens.TouchTargetMin)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "¥",
                style = editorAmountSymbol,
                color = amountColor.copy(alpha = 0.65f),
            )
            Text(
                text = if (hasDigits) state.amountInput else "0.00",
                style = displayEditorAmount,
                color = amountColor,
                maxLines = 1,
            )
        }
    }
}

/** 底部胶囊行(原型 .chip-strip:账户 / 时间 / 标签 / 备注 / AI)。 */
@Composable
private fun ChipStrip(
    state: TxnEditUiState,
    onPickAccount: () -> Unit,
    onPickDateTime: () -> Unit,
    onPickTags: () -> Unit,
    onPickRemark: () -> Unit,
    onEnterAi: () -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val accountName = state.accounts.firstOrNull { it.id == state.accountId }?.name
        ?: stringResource(R.string.txn_edit_account)
    val tagNames = state.tags.filter { it.id in state.selectedTagIds }.joinToString("、") { it.name }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!state.type.requiresTargetAccount) {
            PillChip(
                icon = Icons.Outlined.AccountBalanceWallet,
                label = accountName,
                onClick = onPickAccount,
            )
        }
        PillChip(
            icon = Icons.Outlined.CalendarMonth,
            label = "${relativeDateLabel(state)} ${timeLabel(state.occurredAt, zone)}",
            onClick = onPickDateTime,
        )
        PillChip(
            icon = Icons.Outlined.Label,
            label = tagNames.ifBlank { stringResource(R.string.txn_edit_tags) },
            onClick = onPickTags,
        )
        PillChip(
            icon = Icons.Outlined.EditNote,
            label = stringResource(R.string.txn_edit_field_remark),
            onClick = onPickRemark,
        )
        PillChip(
            icon = Icons.Filled.AutoAwesome,
            label = stringResource(R.string.txn_edit_ai_input),
            onClick = onEnterAi,
            highlight = true,
        )
    }
}

@Composable
private fun relativeDateLabel(state: TxnEditUiState): String {
    val date = state.occurredAt.toLocalDate(TimeZone.currentSystemDefault())
    return when {
        state.occurredAt >= state.todayMillis -> stringResource(R.string.txn_edit_date_quick_today)
        state.occurredAt in state.yesterdayMillis until state.todayMillis ->
            stringResource(R.string.txn_edit_date_quick_yesterday)
        else -> monthDayLabel(date)
    }
}

@Composable
private fun PillChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusFull),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 34.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                color = if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

/** 底部停靠:3×4 数字键盘 + 右侧保存竖条(原型 .pad-row)。 */
@Composable
private fun KeypadRow(state: TxnEditUiState, onAction: (TxnEditAction) -> Unit) {
    val rowHeight = 46.dp
    val gap = 6.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight * KEYPAD_ROWS.size + gap * (KEYPAD_ROWS.size - 1)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            KEYPAD_ROWS.forEach { keys ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    keys.forEach { key ->
                        KeypadKey(
                            key = key,
                            onKey = { onAction(TxnEditAction.UpdateAmount(keypadAppend(state.amountInput, key))) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        SaveBar(
            enabled = saveEnabled(state),
            onClick = { onAction(TxnEditAction.Save) },
            modifier = Modifier.width(76.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun KeypadKey(key: String, onKey: () -> Unit, modifier: Modifier = Modifier) {
    val isDelete = key == "⌫"
    Surface(
        modifier = modifier.clickable(onClick = onKey),
        color = if (isDelete) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = if (isDelete) null
        else BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isDelete) {
                Icon(
                    imageVector = Icons.Outlined.Backspace,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = key,
                    style = keypadKey,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 保存竖条(原型 .pad-side .save-btn:墨绿渐变 + 象牙金白字)。 */
@Composable
private fun SaveBar(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accents = vipLedgerAccentColors()
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(13.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled) Modifier.background(
                        Brush.linearGradient(listOf(accents.heroGradientStart, accents.heroGradientEnd)),
                    )
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.common_save),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 数字键盘输入规则:一位小数、最多两位小数、总长上限 12。 */
internal fun keypadAppend(current: String, key: String): String {
    if (key == "⌫") return current.dropLast(1)
    if (key == ".") return if (current.contains('.')) current else current + "."
    if (current.length >= 12) return current
    val dot = current.indexOf('.')
    if (dot >= 0 && current.length - dot > 2) return current
    return current + key
}

/** 保存可用性:金额合法(调整允许负数、其余必须为正)且不在执行中。 */
internal fun saveEnabled(state: TxnEditUiState): Boolean {
    if (state.isBusy || state.editingUnavailable) return false
    val parsed = Money.parse(state.amountInput) ?: return false
    return when (state.type) {
        TransactionType.ADJUST -> !parsed.isZero
        else -> parsed.isPositive
    }
}

// ── 选择器弹层 ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerSheet(
    state: TxnEditUiState,
    side: AccountSheetSide,
    onAction: (TxnEditAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates = if (side == AccountSheetSide.TARGET) state.targetCandidates else state.accounts
    val selectedId = if (side == AccountSheetSide.TARGET) state.toAccountId else state.accountId
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = VipLedgerTokens.Space5)) {
            Text(
                text = if (side == AccountSheetSide.TARGET) stringResource(R.string.txn_edit_account_to)
                else stringResource(R.string.txn_edit_pick_account_hint),
                style = serifTitle,
                color = MaterialTheme.colorScheme.primary,
            )
            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.txn_edit_account_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = VipLedgerTokens.Space4),
                )
            } else {
                candidates.forEach { account ->
                    val selected = account.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = VipLedgerTokens.TouchTargetMin)
                            .clickable {
                                if (side == AccountSheetSide.TARGET) {
                                    onAction(TxnEditAction.SelectTargetAccount(account.id))
                                } else {
                                    onAction(TxnEditAction.SelectAccount(account.id))
                                }
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = accountTypeLabel(account.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space6))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerSheet(
    state: TxnEditUiState,
    zone: TimeZone,
    onAction: (TxnEditAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedDate = state.occurredAt.toLocalDate(zone)
    var month by remember { mutableStateOf(LocalDate(selectedDate.year, selectedDate.monthNumber, 1)) }
    val time = state.occurredAt.toLocalDateTime(zone).time
    // 时间步进:本地值随 occurredAt 变化而同步(SelectNow / SelectTime 都会改 occurredAt)。
    LaunchedEffect(state.occurredAt) {
        month = LocalDate(
            state.occurredAt.toLocalDate(zone).year,
            state.occurredAt.toLocalDate(zone).monthNumber,
            1,
        )
    }
    val weekdays = stringArrayResource(R.array.ledger_weekdays)
    val today = state.todayMillis.toLocalDate(zone)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = VipLedgerTokens.Space4)) {
            Text(
                text = stringResource(R.string.txn_edit_pick_datetime),
                style = serifTitle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space3))
            // 月历
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { month = month.minus(1, DateTimeUnit.MONTH) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.txn_edit_date_pick))
                }
                Text(
                    text = stringResource(R.string.txn_edit_sheet_month, month.year, month.monthNumber),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = { month = month.plus(1, DateTimeUnit.MONTH) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.txn_edit_date_pick))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f).padding(vertical = VipLedgerTokens.Space1),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            calendarWeeks(month).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            DayCell(
                                date = date,
                                selected = date == selectedDate,
                                isToday = date == today,
                                onClick = { onAction(TxnEditAction.SelectDate(date)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space4))
            // 时:分:秒 步进
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeStepColumn(
                    value = time.hour,
                    label = stringResource(R.string.txn_edit_time_hour),
                    onChange = { onAction(TxnEditAction.SelectTime(LocalTime(it, time.minute, time.second))) },
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = VipLedgerTokens.Space2, vertical = VipLedgerTokens.Space5),
                )
                TimeStepColumn(
                    value = time.minute,
                    label = stringResource(R.string.txn_edit_time_minute),
                    onChange = { onAction(TxnEditAction.SelectTime(LocalTime(time.hour, it, time.second))) },
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = VipLedgerTokens.Space2, vertical = VipLedgerTokens.Space5),
                )
                TimeStepColumn(
                    value = time.second,
                    label = stringResource(R.string.txn_edit_time_second),
                    onChange = { onAction(TxnEditAction.SelectTime(LocalTime(time.hour, time.minute, it))) },
                )
            }
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space4))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.txn_edit_time_now),
                    onClick = { onAction(TxnEditAction.SelectNow) },
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.txn_edit_datetime_done),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space6))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
            )
            .then(
                if (isToday && !selected) Modifier.border(
                    VipLedgerTokens.BorderThin,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(10.dp),
                ) else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TimeStepColumn(value: Int, label: String, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { onChange((value + 1) % 60) }) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
        }
        Text(
            text = value.toString().padStart(2, '0'),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 34.sp,
            modifier = Modifier.padding(horizontal = VipLedgerTokens.Space1),
        )
        IconButton(onClick = { onChange((value + 59) % 60) }) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSheet(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val visibleTags = if (state.tagInput.isBlank()) state.tags else state.matchingTags
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = VipLedgerTokens.Space5)) {
            Text(
                text = stringResource(R.string.txn_edit_tags),
                style = serifTitle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space3))
            LabeledTextField(
                label = stringResource(R.string.txn_edit_tags),
                value = state.tagInput,
                onValueChange = { onAction(TxnEditAction.UpdateTagInput(it)) },
                placeholder = stringResource(R.string.txn_edit_tag_search_placeholder),
            )
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space3))
            when {
                state.tagInput.isNotBlank() && state.existingTag != null -> Text(
                    text = stringResource(R.string.txn_edit_tag_exists),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.tagInput.isNotBlank() -> PrimaryButton(
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
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space3))
            if (visibleTags.isEmpty()) {
                Text(
                    text = stringResource(R.string.txn_edit_tags_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
                    verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
                ) {
                    visibleTags.forEach { tag ->
                        SelectableChip(
                            label = tag.name,
                            selected = tag.id in state.selectedTagIds,
                            onClick = { onAction(TxnEditAction.ToggleTag(tag.id)) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space6))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemarkSheet(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = VipLedgerTokens.Space5)) {
            Text(
                text = stringResource(R.string.txn_edit_field_remark),
                style = serifTitle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space3))
            LabeledTextField(
                label = stringResource(R.string.txn_edit_field_remark),
                value = state.remark,
                onValueChange = { onAction(TxnEditAction.UpdateRemark(it)) },
                singleLine = false,
            )
            if (state.type.isAccountingForStats) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = VipLedgerTokens.TouchTargetMin),
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
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space3))
            PrimaryButton(
                text = stringResource(R.string.txn_edit_datetime_done),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(VipLedgerTokens.Space6))
        }
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.heightIn(min = VipLedgerTokens.TouchTargetMin).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusSmall),
        border = BorderStroke(
            VipLedgerTokens.BorderThin,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = VipLedgerTokens.Space3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** AI 记账模式(独立子页,保持可滚动;零滚动约束只针对手动录入表单)。 */
@Composable
private fun AiModeColumn(
    state: TxnEditUiState,
    onAction: (TxnEditAction) -> Unit,
    padding: PaddingValues,
    aiInputFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VipLedgerTokens.Space4, vertical = VipLedgerTokens.Space3),
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
            modifier = Modifier.padding(VipLedgerTokens.Space4),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
        ) {
            Text(text = stringResource(R.string.txn_edit_ai_preview), style = MaterialTheme.typography.titleMedium)
            Text(text = transactionTypeLabel(parsed.type), style = MaterialTheme.typography.bodyMedium)
            AmountText(amount = parsed.amount, style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(R.string.txn_edit_ai_preview_account, account))
            Text(text = stringResource(R.string.txn_edit_ai_preview_category, category))
            if (targetAccount != null) {
                Text(text = stringResource(R.string.txn_edit_ai_preview_target, targetAccount))
            }
            Text(text = stringResource(R.string.txn_edit_ai_preview_date, parsed.date.toString()))
            if (parsed.unresolved.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.txn_edit_ai_correction_hint),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
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