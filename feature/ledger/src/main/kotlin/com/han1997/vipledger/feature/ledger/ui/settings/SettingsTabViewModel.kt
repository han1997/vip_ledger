package com.han1997.vipledger.feature.ledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.ObservePendingDraftsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsTabViewModel @Inject constructor(
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
    observePendingDrafts: ObservePendingDraftsUseCase,
) : ViewModel() {
    val currentLedger: StateFlow<Ledger?> = observeCurrentLedger()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingDraftCount: StateFlow<Int> = observeCurrentLedger()
        .flatMapLatest { ledger ->
            val draftsFlow: Flow<List<Txn>> = if (ledger == null) {
                flowOf(emptyList())
            } else {
                observePendingDrafts(ledger.id)
            }
            draftsFlow
        }
        .map { drafts -> drafts.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
