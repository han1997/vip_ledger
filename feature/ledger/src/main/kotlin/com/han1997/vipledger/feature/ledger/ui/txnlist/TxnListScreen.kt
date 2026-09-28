package com.han1997.vipledger.feature.ledger.ui.txnlist

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.ConfirmDialog
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
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.LocalDate

/** 明细列表：按天分组、长按删除。 */
@Composable
fun TxnListScreen(
    onBack: () -> Unit,
    onAddTxn: () -> Unit,
    onEditTxn: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TxnListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TxnListContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAddTxn = onAddTxn,
        onEditTxn = onEditTxn,
        modifier = modifier,
    )
}

@Composable
internal fun TxnListContent(
    state: TxnListUiState,
    onAction: (TxnListAction) -> Unit,
    onBack: () -> Unit,
    onAddTxn: () -> Unit,
    onEditTxn: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Txn?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.txn_deleted)
    val failedMessage = stringResource(R.string.txn_delete_failed)

    LaunchedEffect(state.notice) {
        when (state.notice) {
            TxnListNotice.DELETED -> snackbarHostState.showSnackbar(deletedMessage)
            TxnListNotice.DELETE_FAILED -> snackbarHostState.showSnackbar(failedMessage)
            null -> Unit
        }
        if (state.notice != null) onAction(TxnListAction.ConsumeNotice)
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = state.ledgerName.ifBlank { stringResource(R.string.txn_list_title) },
                onBack = onBack,
                actions = {
                    if (state.totalCount > 0) {
                        Text(
                            text = stringResource(R.string.txn_list_count, state.totalCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTxn,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = stringResource(R.string.home_add_txn)) },
            )
        },
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
                EmptyState(
                    message = stringResource(R.string.txn_list_empty),
                    actionLabel = stringResource(R.string.home_add_txn),
                    onAction = onAddTxn,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header-" + group.date.toString()) {
                        DayHeader(group)
                    }
                    items(
                        items = group.txns,
                        key = { it.id },
                    ) { txn ->
                        Column {
                            TxnLine(
                                txn = txn,
                                categoryName = txn.categoryId?.let { state.categoryNames[it] },
                                accountName = state.accountNames[txn.accountId],
                                tagNames = txn.tagIds.mapNotNull { state.tagNames[it] },
                                showTime = true,
                                onClick = { onEditTxn(txn.id) },
                                onLongClick = { pendingDelete = txn },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { txn ->
        ConfirmDialog(
            title = stringResource(R.string.txn_delete_title),
            message = stringResource(R.string.txn_delete_message),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                pendingDelete = null
                onAction(TxnListAction.Delete(txn.id))
            },
            onDismiss = { pendingDelete = null },
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
                R.string.txn_list_day_total,
                group.income.toDisplayString(),
                group.expense.toDisplayString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TxnListContentPreview() {
    VipLedgerTheme {
        TxnListContent(
            state = TxnListUiState(
                isLoading = false,
                ledgerName = "默认账本",
                totalCount = 2,
                accountNames = mapOf("a1" to "现金"),
                categoryNames = mapOf("c1" to "餐饮"),
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
            ),
            onAction = {},
            onBack = {},
            onAddTxn = {},
            onEditTxn = {},
        )
    }
}
