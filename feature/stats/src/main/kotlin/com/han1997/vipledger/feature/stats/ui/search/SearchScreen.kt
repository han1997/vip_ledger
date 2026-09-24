package com.han1997.vipledger.feature.stats.ui.search

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.ConfirmDialog
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.TxnLine
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.dayHeaderLabel
import com.han1997.vipledger.core.designsystem.format.transactionTypeLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDayGroup
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.stats.R
import com.han1997.vipledger.feature.stats.ui.common.AmountRangeFields
import com.han1997.vipledger.feature.stats.ui.common.ChoiceChips
import com.han1997.vipledger.feature.stats.ui.common.DateRangePickerDialog
import com.han1997.vipledger.feature.stats.ui.common.FilterOption
import com.han1997.vipledger.feature.stats.ui.common.FilterSection
import com.han1997.vipledger.feature.stats.ui.common.TimeRangeSelector
import com.han1997.vipledger.feature.stats.ui.common.TxnFilterState
import com.han1997.vipledger.feature.stats.ui.common.writeTextToUri
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** 组合搜索：上半个屏幕选条件，下面实时出结果（按天分组、可编辑可删除可导出）。 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onEditTxn: (ledgerId: String, txnId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onEditTxn = onEditTxn,
        modifier = modifier,
    )
}

@Composable
internal fun SearchContent(
    state: SearchUiState,
    onAction: (SearchAction) -> Unit,
    onBack: () -> Unit,
    onEditTxn: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Txn?>(null) }
    var pickCustomRange by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CSV_MIME),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        val success = uri != null && csv != null && writeTextToUri(context, uri, csv)
        onAction(SearchAction.ExportFinished(success))
    }

    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
        pendingCsv = export.csv
        createDocument.launch(export.fileName)
        onAction(SearchAction.ConsumeExport)
    }

    val noticeMessage = state.notice?.let { searchNoticeMessage(it) }
    LaunchedEffect(state.notice) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(SearchAction.ConsumeNotice)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = state.ledgerName.ifBlank { stringResource(R.string.search_title) },
                onBack = onBack,
                actions = {
                    if (state.filter.hasConditions) {
                        TextButton(onClick = { onAction(SearchAction.ClearConditions) }) {
                            Text(text = stringResource(R.string.search_clear))
                        }
                    }
                    if (state.totalCount > 0) {
                        TextButton(onClick = { onAction(SearchAction.ExportCsv) }) {
                            Text(text = stringResource(R.string.search_export))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            SearchBody(
                state = state,
                padding = padding,
                zone = zone,
                onAction = onAction,
                onEditTxn = onEditTxn,
                onPickCustomRange = { pickCustomRange = true },
                onLongPress = { pendingDelete = it },
            )
        }
    }

    if (pickCustomRange) {
        DateRangePickerDialog(
            zone = zone,
            initialRange = state.filter.customRange,
            onDismiss = { pickCustomRange = false },
            onConfirm = { range ->
                pickCustomRange = false
                onAction(SearchAction.SelectCustomRange(range))
            },
        )
    }

    pendingDelete?.let { txn ->
        ConfirmDialog(
            title = stringResource(R.string.search_delete_title),
            message = stringResource(R.string.search_delete_message),
            confirmLabel = stringResource(R.string.stats_action_delete),
            dismissLabel = stringResource(R.string.stats_action_cancel),
            onConfirm = {
                pendingDelete = null
                onAction(SearchAction.Delete(txn.id))
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** 条件区 + 结果列表（同一个 `LazyColumn`，整页只有一条滚动轴）。 */
@Composable
private fun SearchBody(
    state: SearchUiState,
    padding: PaddingValues,
    zone: TimeZone,
    onAction: (SearchAction) -> Unit,
    onEditTxn: (String, String) -> Unit,
    onPickCustomRange: () -> Unit,
    onLongPress: (Txn) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "conditions") {
            SearchConditions(
                state = state,
                onAction = onAction,
                onPickCustomRange = onPickCustomRange,
            )
        }

        item(key = "summary") {
            ResultSummary(state = state)
        }

        if (state.groups.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    message = stringResource(
                        if (state.filter.hasConditions) {
                            R.string.search_empty
                        } else {
                            R.string.search_empty_initial
                        },
                    ),
                )
            }
        }

        state.groups.forEach { group ->
            item(key = "header-" + group.date.toString()) {
                DayHeader(group = group)
            }
            items(items = group.txns, key = { it.id }) { txn ->
                Column {
                    TxnLine(
                        txn = txn,
                        categoryName = txn.categoryId?.let { state.categoryNames[it] },
                        accountName = state.accountNames[txn.accountId],
                        tagNames = txn.tagIds.mapNotNull { state.tagNames[it] },
                        zone = zone,
                        showTime = true,
                        onClick = { onEditTxn(state.ledgerId, txn.id) },
                        onLongClick = { onLongPress(txn) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun SearchConditions(
    state: SearchUiState,
    onAction: (SearchAction) -> Unit,
    onPickCustomRange: () -> Unit,
) {
    val filter = state.filter
    val typeOptions = TransactionType.entries.map {
        FilterOption(id = it.name, label = transactionTypeLabel(it))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LabeledTextField(
            label = stringResource(R.string.search_keyword_label),
            value = filter.keyword,
            onValueChange = { onAction(SearchAction.KeywordChanged(it)) },
            placeholder = stringResource(R.string.search_keyword_hint),
        )

        FilterSection(stringResource(R.string.search_time_label)) {
            TimeRangeSelector(
                preset = filter.preset,
                customRange = filter.customRange,
                onSelectPreset = { onAction(SearchAction.SelectPreset(it)) },
                onPickCustomRange = onPickCustomRange,
            )
        }

        if (state.categoryOptions.isNotEmpty()) {
            FilterSection(stringResource(R.string.search_category_label)) {
                ChoiceChips(
                    options = state.categoryOptions,
                    selectedIds = filter.categoryIds,
                    onToggle = { onAction(SearchAction.ToggleCategory(it)) },
                )
            }
        }

        if (state.tagOptions.isNotEmpty()) {
            FilterSection(stringResource(R.string.search_tag_label)) {
                ChoiceChips(
                    options = state.tagOptions,
                    selectedIds = filter.tagIds,
                    onToggle = { onAction(SearchAction.ToggleTag(it)) },
                )
            }
        }

        if (state.accountOptions.isNotEmpty()) {
            FilterSection(stringResource(R.string.search_account_label)) {
                ChoiceChips(
                    options = state.accountOptions,
                    selectedIds = filter.accountIds,
                    onToggle = { onAction(SearchAction.ToggleAccount(it)) },
                )
            }
        }

        FilterSection(stringResource(R.string.search_type_label)) {
            ChoiceChips(
                options = typeOptions,
                selectedIds = filter.types.map { it.name }.toSet(),
                onToggle = { name ->
                    TransactionType.entries.firstOrNull { it.name == name }
                        ?.let { onAction(SearchAction.ToggleType(it)) }
                },
            )
        }

        FilterSection(stringResource(R.string.search_amount_label)) {
            AmountRangeFields(
                minText = filter.minAmountText,
                maxText = filter.maxAmountText,
                isInvalid = !filter.isAmountRangeValid,
                onMinChange = { onAction(SearchAction.MinAmountChanged(it)) },
                onMaxChange = { onAction(SearchAction.MaxAmountChanged(it)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.search_include_drafts),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = filter.includeDrafts,
                onCheckedChange = { onAction(SearchAction.IncludeDraftsChanged(it)) },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

@Composable
private fun ResultSummary(state: SearchUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.search_result_count, state.totalCount),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                R.string.search_result_total,
                state.income.toDisplayString(),
                state.expense.toDisplayString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(group: TxnDayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayHeaderLabel(group.date),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                R.string.search_result_total,
                group.income.toDisplayString(),
                group.expense.toDisplayString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun searchNoticeMessage(notice: SearchNotice): String = stringResource(
    when (notice) {
        SearchNotice.DELETED -> R.string.search_deleted
        SearchNotice.DELETE_FAILED -> R.string.search_delete_failed
        SearchNotice.EXPORTED -> R.string.search_exported
        SearchNotice.EXPORT_FAILED -> R.string.search_export_failed
    },
)

private const val CSV_MIME = "text/csv"

@Preview(showBackground = true)
@Composable
private fun SearchContentPreview() {
    VipLedgerTheme {
        SearchContent(
            state = SearchUiState(
                isLoading = false,
                ledgerId = "l1",
                ledgerName = "默认账本",
                filter = TxnFilterState(keyword = "午餐"),
                categoryOptions = listOf(FilterOption("c1", "餐饮")),
                groups = listOf(
                    TxnDayGroup(
                        date = LocalDate(2026, 9, 24),
                        income = Money.ofCents(0),
                        expense = Money.ofCents(3800),
                        txns = listOf(
                            Txn(
                                id = "t1",
                                ledgerId = "l1",
                                type = TransactionType.EXPENSE,
                                amount = Money.ofCents(3800),
                                accountId = "a1",
                                categoryId = "c1",
                                title = "午餐",
                                occurredAt = 0,
                                createdAt = 0,
                                updatedAt = 0,
                            ),
                        ),
                    ),
                ),
                totalCount = 1,
                expense = Money.ofCents(3800),
                accountNames = mapOf("a1" to "现金"),
                categoryNames = mapOf("c1" to "餐饮"),
            ),
            onAction = {},
            onBack = {},
            onEditTxn = { _, _ -> },
        )
    }
}