package com.han1997.vipledger.feature.ledger.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.format.dayHeaderLabel
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
        floatingActionButton = {
            state.ledgerId?.let { id ->
                VipLedgerFab(onClick = { onAdd(id, state.selected) }, contentDescription = stringResource(R.string.ledger_calendar_add))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = VipLedgerTokens.Space3, end = VipLedgerTokens.Space3, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item { EmptyState(message = stringResource(R.string.ledger_load_failed), actionLabel = stringResource(R.string.ledger_retry), onAction = { onAction(CalendarAction.Retry) }) }
                state.ledgerId == null -> item { EmptyState(message = stringResource(R.string.home_no_ledger), actionLabel = stringResource(R.string.ledger_manage_add), onAction = onOpenLedgers) }
                else -> {
                    item {
                        MonthCalendarCard(
                            month = state.month,
                            days = state.days.associateBy { it.start },
                            selected = state.selected,
                            today = state.today,
                            onSelect = { onAction(CalendarAction.SelectDate(it)) },
                            header = {
                                MonthNavigation(
                                    month = state.month,
                                    onPrevious = { onAction(CalendarAction.MoveMonth(-1)) },
                                    onNext = { onAction(CalendarAction.MoveMonth(1)) },
                                    onToday = { onAction(CalendarAction.Today) },
                                )
                            },
                        )
                    }
                    item {
                        DaySummary(
                            income = state.selectedGroup?.income ?: Money.ZERO,
                            expense = state.selectedGroup?.expense ?: Money.ZERO,
                        )
                    }
                    item { SectionHeader(title = dayHeaderLabel(state.selected)) }
                    if (state.selectedGroup == null) item { EmptyState(stringResource(R.string.ledger_calendar_empty)) }
                    items(state.selectedGroup?.txns.orEmpty(), key = { it.id }) { txn ->
                        TxnLine(
                            txn = txn,
                            categoryName = txn.categoryId?.let(state.categoryNames::get),
                            accountName = state.accountNames[txn.accountId],
                            tagNames = txn.tagIds.mapNotNull(state.tagNames::get),
                            onClick = { onEdit(requireNotNull(state.ledgerId), txn.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 月历卡（原型 .card 内含 .cal-head + .cal-grid）：7 列，今天金色，选中墨绿渐变，有账日金点。 */
@Composable
private fun MonthCalendarCard(
    month: LocalDate,
    days: Map<LocalDate, PeriodStat>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    header: @Composable () -> Unit,
) {
    val weekdays = stringArrayResource(R.array.ledger_weekdays)
    GroupCard {
        Column(Modifier.padding(VipLedgerTokens.Space3), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
            header()
            Row(Modifier.fillMaxWidth()) {
                weekdays.forEach { day ->
                    Box(
                        Modifier.weight(1f).padding(vertical = VipLedgerTokens.Space2),
                        contentAlignment = Alignment.Center,
                    ) { Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            calendarWeeks(month).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
                    week.forEach { date ->
                        if (date == null) Spacer(Modifier.weight(1f))
                        else CalendarCell(
                            date = date,
                            stat = days[date],
                            selected = date == selected,
                            today = date == today,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
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
    val hasTxn = !income.isZero || !expense.isZero
    val accents = vipLedgerAccentColors()
    val description = stringResource(R.string.ledger_calendar_day_description, date.toString(), income.toDisplayString(), expense.toDisplayString())
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(Brush.linearGradient(listOf(accents.heroGradientStart, accents.heroGradientEnd)))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    today -> accents.accentAmber
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
            )
            if (hasTxn) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.onPrimary else accents.accentAmber,
                            RoundedCornerShape(VipLedgerTokens.RadiusFull),
                        ),
                )
            }
        }
    }
}

/** 选中日收支摘要（原型 .cal-sum）：当日支出（赭红）/ 当日收入（松绿）两个并排小盒。 */
@Composable
private fun DaySummary(income: Money, expense: Money) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
        SummaryBox(stringResource(R.string.ledger_home_expense), expense, ExpenseRed, Modifier.weight(1f))
        SummaryBox(stringResource(R.string.ledger_home_income), income, IncomeGreen, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryBox(label: String, amount: Money, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountText(amount = amount, style = displayAmount, color = color)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarPreview() { VipLedgerTheme { CalendarContent(CalendarUiState(loading = false, ledgerId = "preview", selected = LocalDate(2026, 10, 9)), {}, { _, _ -> }, { _, _ -> }, {}) } }