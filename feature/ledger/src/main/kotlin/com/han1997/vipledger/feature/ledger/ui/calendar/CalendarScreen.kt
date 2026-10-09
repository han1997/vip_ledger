package com.han1997.vipledger.feature.ledger.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.domain.model.PeriodStat
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.MonthNavigation
import kotlinx.datetime.LocalDate

@Composable
fun CalendarScreen(onAdd: (String, LocalDate) -> Unit, onEdit: (String, String) -> Unit, onOpenLedgers: () -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarContent(state, viewModel::onAction, onAdd, onEdit, onOpenLedgers)
}

@Composable
internal fun CalendarContent(state: CalendarUiState, onAction: (CalendarAction) -> Unit, onAdd: (String, LocalDate) -> Unit, onEdit: (String, String) -> Unit, onOpenLedgers: () -> Unit) {
    VipLedgerScaffold(
        topBar = { VipLedgerTopBar(title = stringResource(R.string.ledger_calendar_title), actions = { TextButton(onClick = onOpenLedgers) { Text(state.ledgerName.ifBlank { stringResource(R.string.home_switch_ledger) }) } }) },
        floatingActionButton = { state.ledgerId?.let { id -> ExtendedFloatingActionButton(onClick = { onAdd(id, state.selected) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.ledger_calendar_add)) }) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = VipLedgerTokens.Space3, end = VipLedgerTokens.Space3, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            item { MonthNavigation(state.month, { onAction(CalendarAction.MoveMonth(-1)) }, { onAction(CalendarAction.MoveMonth(1)) }, { onAction(CalendarAction.Today) }) }
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(CalendarAction.Retry) }) }
                state.ledgerId == null -> item { EmptyState(message = stringResource(R.string.home_no_ledger), actionLabel = stringResource(R.string.ledger_manage_add), onAction = onOpenLedgers) }
                else -> {
                    item {
                        GroupCard {
                            Column(Modifier.padding(VipLedgerTokens.Space1), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
                                Row(Modifier.fillMaxWidth()) {
                                    stringArrayResource(R.array.ledger_weekdays).forEach { day -> Box(Modifier.weight(1f).padding(vertical = VipLedgerTokens.Space2), contentAlignment = Alignment.Center) { Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                }
                                val days = state.days.associateBy { it.start }
                                calendarWeeks(state.month).forEach { week ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
                                        week.forEach { date ->
                                            if (date == null) Spacer(Modifier.weight(1f))
                                            else CalendarCell(date, days[date], date == state.selected, date == state.today, { onAction(CalendarAction.SelectDate(date)) }, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        GroupCard { Column(Modifier.padding(VipLedgerTokens.Space4)) {
                            Text(stringResource(R.string.ledger_calendar_month_total), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.txn_list_day_total, state.monthIncome.toDisplayString(), state.monthExpense.toDisplayString()), style = MaterialTheme.typography.bodyMedium)
                        } }
                    }
                    item {
                        SectionHeader(title = fullDateLabel(state.selected))
                        Text(stringResource(R.string.txn_list_day_total, (state.selectedGroup?.income ?: Money.ZERO).toDisplayString(), (state.selectedGroup?.expense ?: Money.ZERO).toDisplayString()), style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.selectedGroup == null) item { EmptyState(stringResource(R.string.ledger_calendar_empty)) }
                    items(state.selectedGroup?.txns.orEmpty(), key = { it.id }) { txn ->
                        GroupCard { TxnLine(txn, txn.categoryId?.let(state.categoryNames::get), state.accountNames[txn.accountId], txn.tagIds.mapNotNull(state.tagNames::get), onClick = { onEdit(requireNotNull(state.ledgerId), txn.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(date: LocalDate, stat: PeriodStat?, selected: Boolean, today: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val income = stat?.income ?: Money.ZERO
    val expense = stat?.expense ?: Money.ZERO
    val description = stringResource(R.string.ledger_calendar_day_description, date.toString(), income.toDisplayString(), expense.toDisplayString())
    Surface(onClick = onClick, modifier = modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(VipLedgerTokens.RadiusSmall), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, border = if (today) BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.heightIn(min = VipLedgerTokens.TouchTargetMin).padding(vertical = VipLedgerTokens.Space1), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            Text(if (income.isZero) "" else calendarAmountText(income), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = IncomeGreen)
            Text(if (expense.isZero) "" else calendarAmountText(expense), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = ExpenseRed)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarPreview() { VipLedgerTheme { CalendarContent(CalendarUiState(loading = false, ledgerId = "preview", selected = LocalDate(2026, 10, 9)), {}, { _, _ -> }, { _, _ -> }, {}) } }
