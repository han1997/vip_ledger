package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.domain.model.HomeCard
import com.han1997.vipledger.core.domain.model.HomeCardLayout
import com.han1997.vipledger.core.domain.model.HomeCardSetting
import com.han1997.vipledger.core.domain.usecase.wealth.HomeCardUseCases
import com.han1997.vipledger.core.common.result.isSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeLayoutUiState(val rows: List<HomeCardSetting> = emptyList(), val busy: Boolean = false, val failed: Boolean = false)
sealed interface HomeLayoutAction {
    data class Toggle(val card: HomeCard, val visible: Boolean) : HomeLayoutAction
    data class Move(val card: HomeCard, val delta: Int) : HomeLayoutAction
    data object Reset : HomeLayoutAction
}
@HiltViewModel
class HomeLayoutViewModel @Inject constructor(saved: SavedStateHandle, private val cards: HomeCardUseCases) : ViewModel() {
    private val ledgerId = saved.get<String>("ledgerId").orEmpty()
    private val local = MutableStateFlow(HomeLayoutUiState())
    val uiState = combine(cards.observe(ledgerId), local) { rows, form -> form.copy(rows = rows) }.catch { emit(HomeLayoutUiState(failed = true)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), HomeLayoutUiState())
    fun onAction(action: HomeLayoutAction) {
        if (local.value.busy) return
        val rows = when (action) {
            is HomeLayoutAction.Toggle -> uiState.value.rows.map { if (it.card == action.card) it.copy(visible = action.visible) else it }
            is HomeLayoutAction.Move -> HomeCardLayout.move(uiState.value.rows, action.card, action.delta)
            HomeLayoutAction.Reset -> HomeCardLayout.defaults()
        }
        local.value = HomeLayoutUiState(busy = true)
        viewModelScope.launch {
            try { local.value = HomeLayoutUiState(failed = !cards.save(ledgerId, rows).isSuccess) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { local.value = HomeLayoutUiState(failed = true) }
        }
    }
}
