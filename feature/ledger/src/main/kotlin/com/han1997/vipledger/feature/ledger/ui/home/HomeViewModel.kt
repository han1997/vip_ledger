package com.han1997.vipledger.feature.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.format.tagNameMap
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveHomeOverviewUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveLedgersUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.SelectLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.ObservePendingDraftsUseCase
import com.han1997.vipledger.core.domain.usecase.reminder.ObserveRemindersUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.budget.ObserveBudgetUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveLocalDayUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObservePendingAutoCountUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.HomeCardUseCases
import com.han1997.vipledger.core.domain.usecase.wealth.ObserveSavingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
    observeLedgersUseCase: ObserveLedgersUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    observeTags: ObserveTagsUseCase,
    private val observeHomeOverview: ObserveHomeOverviewUseCase,
    private val observeReminders: ObserveRemindersUseCase,
    private val observePendingDrafts: ObservePendingDraftsUseCase,
    private val selectLedger: SelectLedgerUseCase,
    observeBudget: ObserveBudgetUseCase,
    localDay: ObserveLocalDayUseCase,
    pendingAuto: ObservePendingAutoCountUseCase,
    cards: HomeCardUseCases,
    savings: ObserveSavingsUseCase,
) : ViewModel() {
    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = observeCurrentLedger()
        .distinctUntilChangedBy { ledger -> ledger?.id }
        .combine(retry) { ledger, _ -> ledger }
        .flatMapLatest { ledger ->
            if (ledger == null) {
                flowOf(HomeUiState(isLoading = false))
            } else {
                combine(
                    observeLedgersUseCase(),
                    observeHomeOverview(ledger.id),
                    observeAccounts(ledger.id, includeArchived = true),
                    observeCategoryTree(ledger.id, includeArchived = true),
                    observeTags(ledger.id),
                ) { ledgers, overview, accounts, categoryNodes, tags ->
                    HomeUiState(
                        isLoading = false,
                        ledgerId = ledger.id,
                        ledgerName = overview.ledgerName,
                        ledgers = ledgers,
                        overview = overview,
                        accountNames = accounts.accountNameMap(),
                        categoryNames = categoryNodes.categoryNameMap(),
                        tagNames = tags.tagNameMap(),
                    )
                }
                    .combine(observeReminders(ledger.id)) { state, reminders ->
                        state.copy(reminders = reminders)
                    }
                    .combine(observePendingDrafts(ledger.id)) { state, drafts ->
                        state.copy(pendingDraftCount = drafts.size)
                    }
                    .combine(localDay().flatMapLatest { day -> observeBudget(ledger.id, day.date, day.zone) }) { state, budget -> state.copy(budget = budget) }
                    .combine(localDay()) { state, day -> state.copy(today = day.date) }
                    .combine(pendingAuto(ledger.id)) { state, count -> state.copy(pendingAutoCount = count) }
                    .combine(cards.observe(ledger.id)) { state, settings -> state.copy(cards = settings) }
                    .combine(savings(ledger.id)) { state, goals -> state.copy(savings = goals.filterNot { it.goal.archived }) }
                    .onStart { emit(HomeUiState()) }
                    .catch { emit(HomeUiState(isLoading = false, failed = true)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), HomeUiState())

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Retry -> retry.value++
            is HomeAction.SelectLedger -> viewModelScope.launch { selectLedger(action.ledgerId) }
        }
    }
}
