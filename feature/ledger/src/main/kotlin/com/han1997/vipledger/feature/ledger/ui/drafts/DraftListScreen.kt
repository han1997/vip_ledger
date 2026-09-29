package com.han1997.vipledger.feature.ledger.ui.drafts

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.TxnLine
import com.han1997.vipledger.core.designsystem.component.VipLedgerConfirmDialog
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R

/** 待确认草稿页：逐条确认 / 丢弃，也可一次全部确认。 */
@Composable
fun DraftListScreen(
    onBack: () -> Unit,
    onOpenRecurring: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DraftListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DraftListContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onOpenRecurring = onOpenRecurring,
        modifier = modifier,
    )
}

@Composable
internal fun DraftListContent(
    state: DraftListUiState,
    onAction: (DraftAction) -> Unit,
    onBack: () -> Unit,
    onOpenRecurring: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var discardTarget by remember { mutableStateOf<Txn?>(null) }

    val noticeMessage: String? = when (state.notice) {
        DraftNotice.CONFIRMED -> stringResource(R.string.draft_confirmed)
        DraftNotice.DISCARDED -> stringResource(R.string.draft_discarded)
        DraftNotice.FAILED -> state.noticeDetail ?: stringResource(R.string.notice_failed)
        null -> null
    }
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(DraftAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.draft_title),
                onBack = onBack,
                actions = {
                    if (state.drafts.isNotEmpty()) {
                        TextButton(
                            onClick = { onAction(DraftAction.ConfirmAll) },
                            enabled = !state.confirmingAll,
                        ) {
                            Text(text = stringResource(R.string.draft_confirm_all))
                        }
                    }
                },
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState()
            }

            state.drafts.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    message = stringResource(R.string.draft_empty),
                    actionLabel = stringResource(R.string.home_recurring),
                    onAction = { onOpenRecurring(state.ledgerId) },
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = state.drafts, key = { it.id }) { txn ->
                    DraftCard(
                        txn = txn,
                        accountName = state.accountNames[txn.accountId],
                        categoryName = txn.categoryId?.let { state.categoryNames[it] },
                        tagNames = txn.tagIds.mapNotNull { state.tagNames[it] },
                        busy = txn.id in state.busyIds,
                        onConfirm = { onAction(DraftAction.Confirm(txn.id)) },
                        onDiscard = { discardTarget = txn },
                    )
                }
            }
        }
    }

    discardTarget?.let { target ->
        VipLedgerConfirmDialog(
            title = stringResource(R.string.draft_discard_title),
            message = stringResource(R.string.draft_discard_message),
            confirmLabel = stringResource(R.string.draft_discard),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                onAction(DraftAction.Discard(target.id))
                discardTarget = null
            },
            onDismiss = { discardTarget = null },
            danger = true,
        )
    }
}

@Composable
private fun DraftCard(
    txn: Txn,
    accountName: String?,
    categoryName: String?,
    tagNames: List<String>,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    GroupCard {
        Column(
            modifier = Modifier.padding(vertical = VipLedgerTokens.Space1),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TxnLine(
                txn = txn,
                categoryName = categoryName,
                accountName = accountName,
                tagNames = tagNames,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VipLedgerTokens.Space3),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDiscard, enabled = !busy) {
                    Text(
                        text = stringResource(R.string.draft_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onConfirm, enabled = !busy) {
                    Text(text = stringResource(R.string.draft_confirm))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DraftListPreview() {
    VipLedgerTheme {
        DraftListContent(
            state = DraftListUiState(
                isLoading = false,
                ledgerId = "l1",
                drafts = listOf(
                    Txn(
                        id = "t1",
                        ledgerId = "l1",
                        type = TransactionType.EXPENSE,
                        amount = Money.ofCents(200000),
                        accountId = "a1",
                        title = "房租",
                        occurredAt = 0,
                        createdAt = 0,
                        updatedAt = 0,
                    ),
                ),
                accountNames = mapOf("a1" to "现金"),
            ),
            onAction = {},
            onBack = {},
            onOpenRecurring = {},
        )
    }
}
