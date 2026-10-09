package com.han1997.vipledger.feature.ledger.ui.calendar

import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlinx.datetime.*

data class CalendarUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val ledgerId: String? = null,
    val ledgerName: String = "",
    val selected: LocalDate = LocalDate(1970, 1, 1),
    val today: LocalDate = LocalDate(1970, 1, 1),
    val days: List<PeriodStat> = emptyList(),
    val groups: List<TxnDayGroup> = emptyList(),
    val accountNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val tagNames: Map<String, String> = emptyMap(),
) {
    val month: LocalDate get() = DateRangePreset.startOfMonth(selected)
    val monthIncome: Money get() = Money.sum(days.map { it.income })
    val monthExpense: Money get() = Money.sum(days.map { it.expense })
    val selectedGroup: TxnDayGroup? get() = groups.find { it.date == selected }
}

/** 固定周一开头，按实际月长补足整周；无跨月格可点击。 */
fun calendarWeeks(month: LocalDate): List<List<LocalDate?>> {
    val range = DateRangePreset.THIS_MONTH.resolve(month)
    val cells = MutableList<LocalDate?>(range.start.dayOfWeek.ordinal) { null }
    var current = range.start
    while (current <= range.endInclusive) { cells += current; current = current.plus(1, DateTimeUnit.DAY) }
    while (cells.size % 7 != 0) cells += null
    return cells.chunked(7)
}

/** 日历窄格省略无意义的小数零；选中日摘要与无障碍说明保留完整金额。 */
fun calendarAmountText(amount: Money): String = amount.toPlainString().trimEnd('0').trimEnd('.')

sealed interface CalendarAction {
    data class SelectDate(val date: LocalDate) : CalendarAction
    data class MoveMonth(val delta: Int) : CalendarAction
    data object Today : CalendarAction
    data object Retry : CalendarAction
}
