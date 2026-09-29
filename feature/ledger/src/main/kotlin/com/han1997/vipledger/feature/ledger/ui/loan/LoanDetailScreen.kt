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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.usecase.loan.InstallmentPlanDetail
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.installmentMethodLabel
import kotlinx.datetime.LocalDate

/** 分期计划详情页：每期明细 + 逐期还款。 */
@Composable
fun LoanDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoanDetailContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun LoanDetailContent(
    state: LoanDetailUiState,
    onAction: (LoanDetailAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeMessage: String? = when (state.notice) {
        LoanNotice.REPAID -> stringResource(R.string.loan_repay_done)
        LoanNotice.DELETED -> stringResource(R.string.notice_deleted)
        LoanNotice.FAILED -> state.noticeDetail ?: stringResource(R.string.notice_failed)
        null -> null
    }
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(LoanDetailAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = state.detail?.plan?.name ?: stringResource(R.string.loan_detail_title),
                onBack = onBack,
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        val detail = state.detail
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState()
            }

            detail == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(message = stringResource(R.string.loan_empty))
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { PlanHeader(detail) }
                items(items = detail.periods, key = { it.id }) { period ->
                    PeriodRow(
                        period = period,
                        repayEnabled = !state.repaying,
                        onRepay = { onAction(LoanDetailAction.StartRepay(period.periodNo)) },
                    )
                }
            }
        }
    }

    val repayPeriod = state.detail?.periods?.firstOrNull { it.periodNo == state.repayPeriodNo }
    if (repayPeriod != null) {
        RepayDialog(
            state = state,
            period = repayPeriod,
            onSelectAccount = { onAction(LoanDetailAction.SelectRepayAccount(it)) },
            onConfirm = { onAction(LoanDetailAction.ConfirmRepay) },
            onDismiss = { onAction(LoanDetailAction.CancelRepay) },
        )
    }
}

@Composable
private fun PlanHeader(detail: InstallmentPlanDetail) {
    GroupCard {
        Column(
            modifier = Modifier.padding(VipLedgerTokens.Space4),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
        ) {
            Text(
                text = installmentMethodLabel(detail.plan.method),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.loan_progress, detail.paidCount, detail.periods.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.loan_remaining,
                    detail.remainingAmount.toDisplayString(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            detail.nextPeriod?.let { next ->
                Text(
                    text = stringResource(R.string.loan_next_due, fullDateLabel(next.dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeriodRow(
    period: InstallmentPeriod,
    repayEnabled: Boolean,
    onRepay: () -> Unit,
) {
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VipLedgerTokens.Space4, vertical = VipLedgerTokens.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.loan_period_no, period.periodNo),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.loan_period_due, fullDateLabel(period.dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.loan_period_interest,
                        period.principal.toDisplayString(),
                        period.interest.toDisplayString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AmountText(amount = period.total)
                if (period.status == InstallmentPeriodStatus.PAID) {
                    Text(
                        text = stringResource(R.string.loan_period_paid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TextButton(onClick = onRepay, enabled = repayEnabled) {
                        Text(text = stringResource(R.string.loan_repay))
                    }
                }
            }
        }
    }
}

@Composable
private fun RepayDialog(
    state: LoanDetailUiState,
    period: InstallmentPeriod,
    onSelectAccount: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.loan_repay_title, period.periodNo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.loan_repay_amount,
                        period.total.toDisplayString(),
                    ),
                )
                Text(
                    text = stringResource(R.string.loan_repay_from),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.repayAccounts.forEach { account ->
                    FilterChip(
                        selected = state.repayAccountId == account.id,
                        onClick = { onSelectAccount(account.id) },
                        label = { Text(text = account.name) },
                    )
                }
                if (state.repayAccounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.loan_no_debt_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.repayAccountId != null && !state.repaying,
            ) {
                Text(text = stringResource(R.string.loan_repay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun LoanDetailPreview() {
    VipLedgerTheme {
        LoanDetailContent(
            state = LoanDetailUiState(
                isLoading = false,
                planId = "p1",
                detail = InstallmentPlanDetail(
                    plan = InstallmentPlan(
                        id = "p1",
                        ledgerId = "l1",
                        accountId = "a1",
                        name = "花呗 12 期",
                        method = InstallmentMethod.EQUAL_INSTALLMENT,
                        principal = Money.ofCents(1200000),
                        periods = 2,
                        firstDueDate = LocalDate(2026, 2, 10),
                        createdAt = 0,
                        updatedAt = 0,
                    ),
                    periods = listOf(
                        InstallmentPeriod(
                            id = "s1",
                            planId = "p1",
                            periodNo = 1,
                            dueDate = LocalDate(2026, 2, 10),
                            principal = Money.ofCents(100000),
                            interest = Money.ofCents(1000),
                            status = InstallmentPeriodStatus.PAID,
                        ),
                        InstallmentPeriod(
                            id = "s2",
                            planId = "p1",
                            periodNo = 2,
                            dueDate = LocalDate(2026, 3, 10),
                            principal = Money.ofCents(100000),
                            interest = Money.ofCents(900),
                        ),
                    ),
                ),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
