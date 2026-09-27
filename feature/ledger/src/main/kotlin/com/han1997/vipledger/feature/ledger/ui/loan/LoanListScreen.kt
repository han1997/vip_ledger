package com.han1997.vipledger.feature.ledger.ui.loan

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.usecase.loan.InstallmentSummary
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.LocalDate

/** 分期计划列表页。 */
@Composable
fun LoanListScreen(
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoanListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoanListContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onCreate = onCreate,
        onOpenDetail = onOpenDetail,
        modifier = modifier,
    )
}

@Composable
internal fun LoanListContent(
    state: LoanListUiState,
    onAction: (LoanListAction) -> Unit,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<InstallmentSummary?>(null) }

    val noticeMessage: String? = when (state.notice) {
        LoanNotice.DELETED -> stringResource(R.string.notice_deleted)
        LoanNotice.REPAID -> stringResource(R.string.loan_repay_done)
        LoanNotice.FAILED -> state.noticeDetail ?: stringResource(R.string.notice_failed)
        null -> null
    }
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(LoanListAction.ConsumeNotice)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.loan_title),
                onBack = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreate(state.ledgerId) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = stringResource(R.string.loan_add)) },
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

            state.summaries.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    message = stringResource(R.string.loan_empty),
                    actionLabel = stringResource(R.string.loan_add),
                    onAction = { onCreate(state.ledgerId) },
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = state.summaries, key = { it.plan.id }) { summary ->
                    LoanSummaryCard(
                        summary = summary,
                        accountName = state.accountNames[summary.plan.accountId],
                        onClick = { onOpenDetail(summary.plan.id) },
                        onDelete = { deleteTarget = summary },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.loan_delete_title),
            message = stringResource(R.string.loan_delete_message, target.plan.name),
            confirmLabel = stringResource(R.string.notice_deleted),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                onAction(LoanListAction.Delete(target.plan.id))
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun LoanSummaryCard(
    summary: InstallmentSummary,
    accountName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = summary.plan.name, style = MaterialTheme.typography.titleMedium)
                if (summary.isClosed) {
                    Text(
                        text = stringResource(R.string.loan_closed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.notice_deleted),
                        )
                    }
                }
            }
            accountName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.loan_progress,
                    summary.paidPeriods,
                    summary.totalPeriods,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (summary.totalPeriods > 0) {
                LinearProgressIndicator(
                    progress = { summary.paidPeriods.toFloat() / summary.totalPeriods.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(
                    R.string.loan_remaining,
                    summary.remainingAmount.toDisplayString(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            summary.nextDueDate?.let { dueDate ->
                Text(
                    text = stringResource(R.string.loan_next_due, fullDateLabel(dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoanListPreview() {
    VipLedgerTheme {
        LoanListContent(
            state = LoanListUiState(
                isLoading = false,
                ledgerId = "l1",
                summaries = listOf(
                    InstallmentSummary(
                        plan = InstallmentPlan(
                            id = "p1",
                            ledgerId = "l1",
                            accountId = "a1",
                            name = "花呗 12 期",
                            method = InstallmentMethod.EQUAL_INSTALLMENT,
                            principal = Money.ofCents(1200000),
                            periods = 12,
                            firstDueDate = LocalDate(2026, 2, 10),
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                        totalPeriods = 12,
                        paidPeriods = 3,
                        remainingAmount = Money.ofCents(900000),
                        nextDueDate = LocalDate(2026, 5, 10),
                    ),
                ),
                accountNames = mapOf("a1" to "花呗"),
            ),
            onAction = {},
            onBack = {},
            onCreate = {},
            onOpenDetail = {},
        )
    }
}
