package com.han1997.vipledger.feature.ledger.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.format.tagNameMap
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveCalendarUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveLocalDayUseCase
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.feature.ledger.ui.common.validCalendarDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    ledger: ObserveCurrentLedgerUseCase,
    day: ObserveLocalDayUseCase,
    calendar: ObserveCalendarUseCase,
    accounts: ObserveAccountsUseCase,
    categories: ObserveCategoryTreeUseCase,
    tags: ObserveTagsUseCase,
) : ViewModel() {
    private val selected = savedState.getStateFlow<String?>("calendarDate", null)
    private val retry = MutableStateFlow(0)
    val uiState = combine(ledger(), day(), selected, retry) { current, local, chosen, _ -> Triple(current, local, validCalendarDate(chosen) ?: local.date) }
        .flatMapLatest { (current, local, chosen) ->
            if (current == null) flowOf(CalendarUiState(loading = false, today = local.date, selected = chosen))
            else combine(calendar(current.id, chosen, local.zone), accounts(current.id, true), categories(current.id, true), tags(current.id)) { report, accts, cats, labels ->
                CalendarUiState(loading = false, ledgerId = current.id, ledgerName = current.name, selected = chosen, today = local.date, days = report.days, groups = report.groups, accountNames = accts.accountNameMap(), categoryNames = cats.categoryNameMap(), tagNames = labels.tagNameMap())
            }.onStart { emit(CalendarUiState(ledgerId = current.id, ledgerName = current.name, today = local.date, selected = chosen)) }
                .catch { emit(CalendarUiState(loading = false, failed = true, ledgerId = current.id, ledgerName = current.name, today = local.date, selected = chosen)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), CalendarUiState())

    fun onAction(action: CalendarAction) {
        when (action) {
            is CalendarAction.SelectDate -> if (action.date.year in 1900..9998) savedState["calendarDate"] = action.date.toString()
            is CalendarAction.MoveMonth -> {
                val date = uiState.value.month.plus(action.delta, DateTimeUnit.MONTH)
                if (date.year in 1900..9998) savedState["calendarDate"] = date.toString()
            }
            CalendarAction.Today -> savedState["calendarDate"] = null
            CalendarAction.Retry -> retry.value++
        }
    }
}
