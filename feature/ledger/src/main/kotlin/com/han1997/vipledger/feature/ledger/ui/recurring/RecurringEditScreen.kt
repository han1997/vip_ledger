package com.han1997.vipledger.feature.ledger.ui.recurring

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
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.accountTypeLabel
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.format.transactionTypeLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.generateModeLabel
import com.han1997.vipledger.feature.ledger.ui.common.missedPolicyLabel
import com.han1997.vipledger.feature.ledger.ui.common.recurrenceEndTypeLabel
import com.han1997.vipledger.feature.ledger.ui.common.recurrenceFrequencyLabel
import com.han1997.vipledger.feature.ledger.ui.common.weekdayOptions
import com.han1997.vipledger.feature.ledger.ui.common.weekdayShortLabel
import kotlinx.datetime.LocalDate

/** 周期规则编辑页。 */
@Composable
fun RecurringEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringEditContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun RecurringEditContent(
    state: RecurringEditUiState,
    onAction: (RecurringEditAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pickingEndDate by remember { mutableStateOf(false) }
    var pickingStartDate by remember { mutableStateOf(false) }
    val failedMessage = stringResource(R.string.common_error_unknown)
    val errorMessage: String? = when (state.error) {
        RecurringEditError.NAME_REQUIRED -> stringResource(R.string.recurring_name_required)
        RecurringEditError.INVALID_AMOUNT -> stringResource(R.string.txn_edit_amount_invalid)
        RecurringEditError.ACCOUNT_REQUIRED -> stringResource(R.string.recurring_account_required)
        RecurringEditError.TARGET_REQUIRED -> stringResource(R.string.recurring_target_required)
        RecurringEditError.INVALID_INTERVAL -> stringResource(R.string.recurring_interval_invalid)
        RecurringEditError.INVALID_END -> stringResource(R.string.recurring_end_invalid)
        RecurringEditError.SAVE_FAILED -> state.saveFailureMessage ?: failedMessage
        null -> null
    }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onAction(RecurringEditAction.ConsumeError)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(
                    if (state.isEditing) R.string.recurring_edit_title else R.string.recurring_create_title,
                ),
                onBack = onBack,
                actions = {
                    TextButton(onClick = { onAction(RecurringEditAction.Save) }) {
                        Text(text = stringResource(R.string.common_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                label = stringResource(R.string.recurring_field_name),
                value = state.name,
                onValueChange = { onAction(RecurringEditAction.UpdateName(it)) },
            )

            SectionLabel(stringResource(R.string.recurring_field_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TransactionType.EXPENSE,
                    TransactionType.INCOME,
                    TransactionType.TRANSFER,
                    TransactionType.DEBT_REPAY,
                ).forEach { type ->
                    FilterChip(
                        selected = state.txnType == type,
                        onClick = { onAction(RecurringEditAction.SelectType(type)) },
                        label = { Text(text = transactionTypeLabel(type)) },
                    )
                }
            }

            LabeledTextField(
                label = stringResource(R.string.recurring_field_amount),
                value = state.amountInput,
                onValueChange = { onAction(RecurringEditAction.UpdateAmount(it)) },
                keyboardType = KeyboardType.Decimal,
                placeholder = stringResource(R.string.txn_edit_amount_placeholder),
            )

            AccountSection(state, onAction)
            CategorySection(state, onAction)

            if (state.tags.isNotEmpty()) {
                SectionLabel(stringResource(R.string.recurring_field_tags))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in state.tagIds,
                            onClick = { onAction(RecurringEditAction.ToggleTag(tag.id)) },
                            label = { Text(text = tag.name) },
                        )
                    }
                }
            }

            LabeledTextField(
                label = stringResource(R.string.recurring_field_title),
                value = state.title,
                onValueChange = { onAction(RecurringEditAction.UpdateTitle(it)) },
            )
            LabeledTextField(
                label = stringResource(R.string.recurring_field_remark),
                value = state.remark,
                onValueChange = { onAction(RecurringEditAction.UpdateRemark(it)) },
                singleLine = false,
            )

            ScheduleSection(state, onAction)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DateRow(
                        label = stringResource(R.string.recurring_start_date),
                        date = state.startDate,
                        onClick = { pickingStartDate = true },
                    )
                    SectionLabel(stringResource(R.string.recurring_end_type))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceEndType.entries.forEach { endType ->
                            FilterChip(
                                selected = state.endType == endType,
                                onClick = { onAction(RecurringEditAction.SelectEndType(endType)) },
                                label = { Text(text = recurrenceEndTypeLabel(endType)) },
                            )
                        }
                    }
                    when (state.endType) {
                        RecurrenceEndType.UNTIL_DATE -> DateRow(
                            label = stringResource(R.string.recurring_end_date),
                            date = state.endDate,
                            onClick = { pickingEndDate = true },
                        )
                        RecurrenceEndType.COUNT -> LabeledTextField(
                            label = stringResource(R.string.recurring_max_occurrences),
                            value = state.maxOccurrencesInput,
                            onValueChange = { onAction(RecurringEditAction.UpdateMaxOccurrences(it)) },
                            keyboardType = KeyboardType.Number,
                        )
                        RecurrenceEndType.NEVER -> Unit
                    }
                }
            }

            SectionLabel(stringResource(R.string.recurring_generate_mode))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GenerateMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.generateMode == mode,
                        onClick = { onAction(RecurringEditAction.SelectGenerateMode(mode)) },
                        label = { Text(text = generateModeLabel(mode)) },
                    )
                }
            }
            SectionLabel(stringResource(R.string.recurring_missed_policy))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MissedPolicy.entries.forEach { policy ->
                    FilterChip(
                        selected = state.missedPolicy == policy,
                        onClick = { onAction(RecurringEditAction.SelectMissedPolicy(policy)) },
                        label = { Text(text = missedPolicyLabel(policy)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.recurring_hint_dates),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickingStartDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDate.toDateColumnEpochMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            onAction(RecurringEditAction.SelectStartDate(picked.toDateColumnLocalDate()))
                        }
                        pickingStartDate = false
                    },
                ) { Text(text = stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        ) { DatePicker(state = pickerState) }
    }

    if (pickingEndDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (state.endDate ?: state.startDate).toDateColumnEpochMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            onAction(RecurringEditAction.SelectEndDate(picked.toDateColumnLocalDate()))
                        }
                        pickingEndDate = false
                    },
                ) { Text(text = stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndDate = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        ) { DatePicker(state = pickerState) }
    }
}

/** `LaunchedEffect` 里不能调 `@Composable`，用简单查表取值。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountSection(state: RecurringEditUiState, onAction: (RecurringEditAction) -> Unit) {
    if (state.accounts.isEmpty()) return
    SectionLabel(stringResource(R.string.recurring_field_account))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.accounts.forEach { account ->
            FilterChip(
                selected = state.accountId == account.id,
                onClick = { onAction(RecurringEditAction.SelectAccount(account.id)) },
                label = { Text(text = account.name + " · " + accountTypeLabel(account.type)) },
            )
        }
    }
    if (state.txnType.requiresTargetAccount) {
        SectionLabel(stringResource(R.string.recurring_field_target))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.accounts.filter { it.id != state.accountId }.forEach { account ->
                FilterChip(
                    selected = state.toAccountId == account.id,
                    onClick = { onAction(RecurringEditAction.SelectTargetAccount(account.id)) },
                    label = { Text(text = account.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(state: RecurringEditUiState, onAction: (RecurringEditAction) -> Unit) {
    val choices = state.categoryChoices()
    if (choices.isEmpty()) return
    SectionLabel(stringResource(R.string.recurring_field_category))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.categoryId == null,
            onClick = { onAction(RecurringEditAction.SelectCategory(null)) },
            label = { Text(text = stringResource(R.string.recurring_pick_none)) },
        )
        choices.forEach { category ->
            FilterChip(
                selected = state.categoryId == category.id,
                onClick = { onAction(RecurringEditAction.SelectCategory(category.id)) },
                label = { Text(text = category.name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSection(state: RecurringEditUiState, onAction: (RecurringEditAction) -> Unit) {
    SectionLabel(stringResource(R.string.recurring_freq))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecurrenceFrequency.entries.forEach { freq ->
            FilterChip(
                selected = state.freq == freq,
                onClick = { onAction(RecurringEditAction.SelectFrequency(freq)) },
                label = { Text(text = recurrenceFrequencyLabel(freq)) },
            )
        }
    }
    if (state.showsIntervalField()) {
        LabeledTextField(
            label = stringResource(
                when (state.freq) {
                    RecurrenceFrequency.WEEKLY -> R.string.recurring_interval_weeks
                    RecurrenceFrequency.YEARLY -> R.string.recurring_interval_years
                    RecurrenceFrequency.EVERY_N_MONTHS, RecurrenceFrequency.MONTHLY ->
                        R.string.recurring_interval_months
                    else -> R.string.recurring_interval_days
                },
            ),
            value = state.intervalInput,
            onValueChange = { onAction(RecurringEditAction.UpdateInterval(it)) },
            keyboardType = KeyboardType.Number,
        )
    }
    if (state.showsWeekdayField()) {
        SectionLabel(stringResource(R.string.recurring_weekday))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weekdayOptions.forEach { weekday ->
                FilterChip(
                    selected = state.weekday == weekday,
                    onClick = { onAction(RecurringEditAction.SelectWeekday(weekday)) },
                    label = { Text(text = weekdayShortLabel(weekday)) },
                )
            }
        }
    }
    if (state.showsMonthDayField()) {
        LabeledTextField(
            label = stringResource(R.string.recurring_month_day),
            value = state.monthDayInput,
            onValueChange = { onAction(RecurringEditAction.UpdateMonthDay(it)) },
            keyboardType = KeyboardType.Number,
            supportingText = stringResource(R.string.recurring_month_day_hint),
        )
    }
    if (state.showsMonthField()) {
        LabeledTextField(
            label = stringResource(R.string.recurring_month),
            value = state.monthInput,
            onValueChange = { onAction(RecurringEditAction.UpdateMonth(it)) },
            keyboardType = KeyboardType.Number,
        )
    }
}

@Composable
private fun DateRow(label: String, date: LocalDate?, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick) {
            Text(text = date?.let { fullDateLabel(it) } ?: stringResource(R.string.txn_edit_date_pick))
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
private fun RecurringEditPreview() {
    VipLedgerTheme {
        RecurringEditContent(
            state = RecurringEditUiState(
                isLoading = false,
                ledgerId = "l1",
                name = "房租",
                amountInput = "2000",
                startDate = LocalDate(2026, 1, 1),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
