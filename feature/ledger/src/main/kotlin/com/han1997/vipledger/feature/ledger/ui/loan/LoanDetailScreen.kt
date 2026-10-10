package com.han1997.vipledger.feature.ledger.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
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
import com.han1997.vipledger.core.domain.usecase.loan.LegacyInstallmentRepayment
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.installmentMethodLabel
import kotlinx.datetime.LocalDate

/** 分期计划详情页：每期明细 + 逐期还款 + 结清 / 提前还款 / 利率变更 / 旧口径修正。 */
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
        LoanNotice.SETTLED -> stringResource(R.string.loan_settled)
        LoanNotice.PREPAID -> stringResource(R.string.loan_prepaid)
        LoanNotice.RATE_CHANGED -> stringResource(R.string.loan_rate_changed)
        LoanNotice.LEGACY_CORRECTED ->
            stringResource(R.string.loan_legacy_corrected, state.legacyCorrectedCount)
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
                item {
                    PlanHeader(
                        detail = detail,
                        state = state,
                        onAction = onAction,
                    )
                }
                if (state.hasLegacy) {
                    item { LegacyBanner(state = state, onAction = onAction) }
                }
                items(items = detail.periods, key = { it.id }) { period ->
                    PeriodRow(
                        period = period,
                        repayEnabled = !state.busy,
                        onRepay = { onAction(LoanDetailAction.StartRepay(period.periodNo)) },
                    )
                }
            }
        }
    }

    val repayPeriod = state.repayPeriod
    if (repayPeriod != null) {
        val invalid = state.repayAmountInvalid()
        AlertDialog(
            onDismissRequest = { onAction(LoanDetailAction.CancelRepay) },
            title = { Text(text = stringResource(R.string.loan_repay_title, repayPeriod.periodNo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.loan_repay_amount,
                            repayPeriod.remainingTotal.toDisplayString(),
                        ),
                    )
                    if (repayPeriod.paidTotal.isPositive) {
                        Text(
                            text = stringResource(
                                R.string.loan_partial_paid,
                                repayPeriod.paidTotal.toDisplayString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LabeledTextField(
                        label = stringResource(R.string.loan_repay_amount_input),
                        value = state.repayAmountInput,
                        onValueChange = { onAction(LoanDetailAction.UpdateRepayAmount(it)) },
                        keyboardType = KeyboardType.Decimal,
                        supportingText = if (invalid) {
                            stringResource(R.string.loan_repay_amount_invalid)
                        } else {
                            stringResource(R.string.loan_repay_hint)
                        },
                    )
                    AccountPicker(state = state, onAction = onAction)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(LoanDetailAction.ConfirmRepay) },
                    enabled = state.repayAccountId != null && !state.busy && !invalid,
                ) {
                    Text(text = stringResource(R.string.loan_repay))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(LoanDetailAction.CancelRepay) }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (state.settleOpen) {
        AlertDialog(
            onDismissRequest = { onAction(LoanDetailAction.CancelSettle) },
            title = {
                Text(text = stringResource(R.string.loan_settle_title, state.detail?.plan?.name.orEmpty()))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.loan_settle_hint))
                    AccountPicker(state = state, onAction = onAction)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(LoanDetailAction.ConfirmSettle) },
                    enabled = state.repayAccountId != null && !state.busy,
                ) {
                    Text(text = stringResource(R.string.loan_settle))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(LoanDetailAction.CancelSettle) }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (state.prepayOpen) {
        val invalid = state.prepayAmountInvalid()
        AlertDialog(
            onDismissRequest = { onAction(LoanDetailAction.CancelPrepay) },
            title = {
                Text(
                    text = stringResource(
                        R.string.loan_prepay_title,
                        state.detail?.remainingPrincipal?.toDisplayString().orEmpty(),
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledTextField(
                        label = stringResource(R.string.loan_prepay_amount),
                        value = state.prepayAmountInput,
                        onValueChange = { onAction(LoanDetailAction.UpdatePrepayAmount(it)) },
                        keyboardType = KeyboardType.Decimal,
                        supportingText = if (invalid) {
                            stringResource(R.string.loan_prepay_amount_invalid)
                        } else {
                            stringResource(R.string.loan_prepay_hint)
                        },
                    )
                    AccountPicker(state = state, onAction = onAction)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(LoanDetailAction.ConfirmPrepay) },
                    enabled = state.repayAccountId != null && !state.busy && !invalid,
                ) {
                    Text(text = stringResource(R.string.loan_prepay))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(LoanDetailAction.CancelPrepay) }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (state.rateOpen) {
        val invalid = state.rateFromPeriodInvalid() || state.rateValueBp() == null
        AlertDialog(
            onDismissRequest = { onAction(LoanDetailAction.CancelRate) },
            title = {
                Text(text = stringResource(R.string.loan_change_rate_title, state.detail?.plan?.name.orEmpty()))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledTextField(
                        label = stringResource(R.string.loan_change_rate_from),
                        value = state.rateFromPeriodInput,
                        onValueChange = { onAction(LoanDetailAction.UpdateRateFrom(it)) },
                        keyboardType = KeyboardType.Number,
                    )
                    LabeledTextField(
                        label = stringResource(R.string.loan_change_rate_value),
                        value = state.rateValueInput,
                        onValueChange = { onAction(LoanDetailAction.UpdateRateValue(it)) },
                        keyboardType = KeyboardType.Decimal,
                        supportingText = stringResource(R.string.loan_change_rate_hint),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(LoanDetailAction.ConfirmRate) },
                    enabled = !state.busy && !invalid,
                ) {
                    Text(text = stringResource(R.string.loan_change_rate))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(LoanDetailAction.CancelRate) }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (state.legacyOpen) {
        AlertDialog(
            onDismissRequest = { onAction(LoanDetailAction.CancelLegacy) },
            title = { Text(text = stringResource(R.string.loan_legacy_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.loan_legacy_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.legacy.forEach { legacy -> LegacyRow(legacy) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(LoanDetailAction.ConfirmLegacy) },
                    enabled = !state.busy,
                ) {
                    Text(text = stringResource(R.string.loan_legacy_correct))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(LoanDetailAction.CancelLegacy) }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun LegacyRow(legacy: LegacyInstallmentRepayment) {
    Text(
        text = stringResource(
            R.string.loan_legacy_row,
            legacy.period.periodNo,
            legacy.legacyAmount.toDisplayString(),
            legacy.period.principal.toDisplayString(),
            legacy.period.interest.toDisplayString(),
            legacy.period.fee.toDisplayString(),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun LegacyBanner(state: LoanDetailUiState, onAction: (LoanDetailAction) -> Unit) {
    GroupCard {
        Column(
            modifier = Modifier.padding(VipLedgerTokens.Space4),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
        ) {
            Text(
                text = stringResource(R.string.loan_legacy_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.loan_legacy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onAction(LoanDetailAction.OpenLegacy) },
                enabled = !state.busy,
            ) {
                Text(text = stringResource(R.string.loan_legacy_correct))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountPicker(state: LoanDetailUiState, onAction: (LoanDetailAction) -> Unit) {
    Text(
        text = stringResource(R.string.loan_repay_from),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.repayAccounts.forEach { account ->
            FilterChip(
                selected = state.repayAccountId == account.id,
                onClick = { onAction(LoanDetailAction.SelectRepayAccount(account.id)) },
                label = { Text(text = account.name) },
            )
        }
    }
    if (state.repayAccounts.isEmpty()) {
        Text(
            text = stringResource(R.string.loan_no_debt_account),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PlanHeader(
    detail: InstallmentPlanDetail,
    state: LoanDetailUiState,
    onAction: (LoanDetailAction) -> Unit,
) {
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
            if (detail.plan.isDisbursement) {
                Text(
                    text = stringResource(
                        R.string.loan_disbursement_banner,
                        detail.plan.disbursementAccountId
                            ?.let { state.accountNames[it] }
                            .orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            Text(
                text = stringResource(
                    R.string.loan_remaining_principal,
                    detail.remainingPrincipal.toDisplayString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detail.nextPeriod?.let { next ->
                Text(
                    text = stringResource(R.string.loan_next_due, fullDateLabel(next.dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail.isClosed) {
                Text(
                    text = stringResource(R.string.loan_closed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { onAction(LoanDetailAction.OpenSettle) },
                        enabled = state.canSettle && !state.busy,
                    ) { Text(text = stringResource(R.string.loan_settle)) }
                    TextButton(
                        onClick = { onAction(LoanDetailAction.OpenPrepay) },
                        enabled = state.canPrepay && !state.busy,
                    ) { Text(text = stringResource(R.string.loan_prepay)) }
                    TextButton(
                        onClick = { onAction(LoanDetailAction.OpenRate) },
                        enabled = state.canChangeRate && !state.busy,
                    ) { Text(text = stringResource(R.string.loan_change_rate)) }
                }
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
                if (period.paidTotal.isPositive && !period.isPaid) {
                    Text(
                        text = stringResource(
                            R.string.loan_partial_paid,
                            period.paidTotal.toDisplayString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AmountText(amount = period.remainingTotal)
                when (period.status) {
                    InstallmentPeriodStatus.PAID -> Text(
                        text = stringResource(R.string.loan_period_paid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    InstallmentPeriodStatus.PARTIAL -> Text(
                        text = stringResource(R.string.loan_period_partial),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    InstallmentPeriodStatus.PENDING -> Unit
                }
                if (!period.isPaid) {
                    TextButton(onClick = onRepay, enabled = repayEnabled) {
                        Text(text = stringResource(R.string.loan_repay))
                    }
                }
            }
        }
    }
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
