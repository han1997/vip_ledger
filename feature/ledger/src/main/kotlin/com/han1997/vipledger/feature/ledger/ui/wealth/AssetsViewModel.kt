package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.domain.usecase.wealth.ObserveAssetsUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetsViewModel @Inject constructor(ledger: ObserveCurrentLedgerUseCase, assets: ObserveAssetsUseCase) : ViewModel() {
    private val archived = MutableStateFlow(false)
    private val retry = MutableStateFlow(0)
    private val base = combine(ledger(), retry) { current, _ -> current }.flatMapLatest { current ->
        if (current == null) flowOf(AssetsUiState(loading = false))
        else assets(current.id).map { AssetsUiState(loading = false, ledgerId = current.id, ledgerName = current.name, report = it) }
            .onStart { emit(AssetsUiState()) }.catch { emit(AssetsUiState(loading = false, failed = true)) }
    }
    val uiState = combine(base, archived) { state, show -> state.copy(showArchived = show) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), AssetsUiState())
    fun onAction(action: AssetsAction) { when (action) { AssetsAction.ToggleArchived -> archived.value = !archived.value; AssetsAction.Retry -> retry.value++ } }
}
