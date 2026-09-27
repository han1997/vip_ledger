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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = observeCurrentLedger()
        .distinctUntilChangedBy { ledger -> ledger?.id }
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
                        ledgerName = ledger.name,
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
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.SelectLedger -> viewModelScope.launch { selectLedger(action.ledgerId) }
        }
    }
}