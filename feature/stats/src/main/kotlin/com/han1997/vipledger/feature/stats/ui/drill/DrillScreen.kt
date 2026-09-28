package com.han1997.vipledger.feature.stats.ui.drill

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.TxnLine
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.dayHeaderLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDayGroup
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.stats.R
import com.han1997.vipledger.feature.stats.ui.common.timeRangeSummary
import com.han1997.vipledger.feature.stats.ui.common.writeTextToUri
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** 统计下钻：固定条件下的明细列表 + 导出。 */
@Composable
fun DrillScreen(
    onBack: () -> Unit,
    onEditTxn: (ledgerId: String, txnId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DrillViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DrillContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onEditTxn = onEditTxn,
        modifier = modifier,
    )
}

@Composable
internal fun DrillContent(
    state: DrillUiState,
    onAction: (DrillAction) -> Unit,
    onBack: () -> Unit,
    onEditTxn: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCsv by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CSV_MIME),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        val success = uri != null && csv != null && writeTextToUri(context, uri, csv)
        onAction(DrillAction.ExportFinished(success))
    }

    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
        pendingCsv = export.csv
        createDocument.launch(export.fileName)
        onAction(DrillAction.ConsumeExport)
    }

    val noticeMessage = state.notice?.let { drillNoticeMessage(it) }
    LaunchedEffect(state.notice) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(DrillAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = state.dimensionLabel ?: stringResource(R.string.drill_title),
                onBack = onBack,
                actions = {
                    if (state.totalCount > 0) {
                        TextButton(onClick = { onAction(DrillAction.ExportCsv) }) {
                            Text(text = stringResource(R.string.drill_export))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.groups.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(message = stringResource(R.string.drill_empty))
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "summary") {
                    DrillSummary(state = state)
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
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrillSummary(state: DrillUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = timeRangeSummary(state.preset, state.range, state.today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.drill_count, state.totalCount),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.drill_total,
                    state.income.toDisplayString(),
                    state.expense.toDisplayString(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                R.string.drill_total,
                group.income.toDisplayString(),
                group.expense.toDisplayString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun drillNoticeMessage(notice: DrillNotice): String = stringResource(
    when (notice) {
        DrillNotice.EXPORTED -> R.string.drill_exported
        DrillNotice.EXPORT_FAILED -> R.string.drill_export_failed
    },
)

private const val CSV_MIME = "text/csv"

@Preview(showBackground = true)
@Composable
private fun DrillContentPreview() {
    VipLedgerTheme {
        DrillContent(
            state = DrillUiState(
                isLoading = false,
                ledgerId = "l1",
                ledgerName = "默认账本",
                dimensionLabel = "餐饮",
                today = LocalDate(2026, 9, 24),
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
