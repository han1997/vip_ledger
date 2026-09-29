package com.han1997.vipledger.feature.ledger.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.installmentMethodLabel
import kotlinx.datetime.LocalDate

/** 新建分期计划表单页。 */
@Composable
fun LoanEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoanEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoanEditContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun LoanEditContent(
    state: LoanEditUiState,
    onAction: (LoanEditAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pickingDate by remember { mutableStateOf(false) }
    val failedMessage = stringResource(R.string.common_error_unknown)
    val errorMessage: String? = when (state.error) {
        LoanEditError.NAME_REQUIRED -> stringResource(R.string.loan_name_required)
        LoanEditError.ACCOUNT_REQUIRED -> stringResource(R.string.loan_account_required)
        LoanEditError.INVALID_PRINCIPAL -> stringResource(R.string.loan_principal_invalid)
        LoanEditError.INVALID_PERIODS -> stringResource(R.string.loan_periods_invalid)
        LoanEditError.INVALID_RATE -> stringResource(R.string.loan_rate_invalid)
        LoanEditError.INVALID_FEE -> stringResource(R.string.loan_fee_invalid)
        LoanEditError.INVALID_DUE_DAY -> stringResource(R.string.loan_due_day_invalid)
        LoanEditError.INVALID_CUSTOM -> stringResource(R.string.loan_custom_invalid)
        LoanEditError.SAVE_FAILED -> state.saveFailureMessage ?: failedMessage
        null -> null
    }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onAction(LoanEditAction.ConsumeError)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.loan_create_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = { onAction(LoanEditAction.Save) }) {
                        Text(text = stringResource(R.string.common_save))
                    }
                },
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledTextField(
                label = stringResource(R.string.loan_name),
                value = state.name,
                onValueChange = { onAction(LoanEditAction.UpdateName(it)) },
            )

            SectionLabel(stringResource(R.string.loan_account))
            if (state.debtAccounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.loan_no_debt_account),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                DebtAccountSection(state, onAction)
            }

            SectionLabel(stringResource(R.string.loan_method))
            MethodSection(state, onAction)

            LabeledTextField(
                label = stringResource(R.string.loan_principal),
                value = state.principalInput,
                onValueChange = { onAction(LoanEditAction.UpdatePrincipal(it)) },
                keyboardType = KeyboardType.Decimal,
            )
            LabeledTextField(
                label = stringResource(R.string.loan_periods),
                value = state.periodsInput,
                onValueChange = { onAction(LoanEditAction.UpdatePeriods(it)) },
                keyboardType = KeyboardType.Number,
            )
            LabeledTextField(
                label = stringResource(R.string.loan_annual_rate),
                value = state.annualRateInput,
                onValueChange = { onAction(LoanEditAction.UpdateAnnualRate(it)) },
                keyboardType = KeyboardType.Decimal,
            )
            LabeledTextField(
                label = stringResource(R.string.loan_upfront_fee),
                value = state.upfrontFeeInput,
                onValueChange = { onAction(LoanEditAction.UpdateUpfrontFee(it)) },
                keyboardType = KeyboardType.Decimal,
            )
            LabeledTextField(
                label = stringResource(R.string.loan_per_period_fee),
                value = state.perPeriodFeeInput,
                onValueChange = { onAction(LoanEditAction.UpdatePerPeriodFee(it)) },
                keyboardType = KeyboardType.Decimal,
            )
            if (state.showsCustomTotalsField()) {
                LabeledTextField(
                    label = stringResource(R.string.loan_custom_totals),
                    value = state.customTotalsInput,
                    onValueChange = { onAction(LoanEditAction.UpdateCustomTotals(it)) },
                    keyboardType = KeyboardType.Decimal,
                    singleLine = false,
                    supportingText = stringResource(R.string.loan_custom_totals_hint),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.loan_first_due_date),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { pickingDate = true }) {
                    Text(text = fullDateLabel(state.firstDueDate))
                }
            }
            LabeledTextField(
                label = stringResource(R.string.loan_due_day),
                value = state.dueDayInput,
                onValueChange = { onAction(LoanEditAction.UpdateDueDay(it)) },
                keyboardType = KeyboardType.Number,
            )
            LabeledTextField(
                label = stringResource(R.string.loan_remark),
                value = state.remark,
                onValueChange = { onAction(LoanEditAction.UpdateRemark(it)) },
                singleLine = false,
            )
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.firstDueDate.toDateColumnEpochMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            onAction(LoanEditAction.SelectFirstDueDate(picked.toDateColumnLocalDate()))
                        }
                        pickingDate = false
                    },
                ) { Text(text = stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebtAccountSection(state: LoanEditUiState, onAction: (LoanEditAction) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.debtAccounts.forEach { account ->
            FilterChip(
                selected = state.accountId == account.id,
                onClick = { onAction(LoanEditAction.SelectAccount(account.id)) },
                label = { Text(text = account.name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MethodSection(state: LoanEditUiState, onAction: (LoanEditAction) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InstallmentMethod.entries.forEach { method ->
            FilterChip(
                selected = state.method == method,
                onClick = { onAction(LoanEditAction.SelectMethod(method)) },
                label = { Text(text = installmentMethodLabel(method)) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun LoanEditPreview() {
    VipLedgerTheme {
        LoanEditContent(
            state = LoanEditUiState(
                isLoading = false,
                ledgerId = "l1",
                name = "花呗 12 期",
                principalInput = "12000",
                periodsInput = "12",
                firstDueDate = LocalDate(2026, 2, 10),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
