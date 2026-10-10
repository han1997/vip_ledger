package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.usecase.wealth.ObserveSavingsUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.SaveSavingsGoalUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.MoveSavingsUseCase
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.model.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavingsViewModel @Inject constructor(saved: SavedStateHandle, observe: ObserveSavingsUseCase, accounts: ObserveAccountsUseCase, private val save: SaveSavingsGoalUseCase, private val move: MoveSavingsUseCase) : ViewModel() {
    private val ledgerId = saved.get<String>("ledgerId").orEmpty()
    private val local = MutableStateFlow(SavingsUiState())
    private val retry = MutableStateFlow(0)
    private val base = retry.flatMapLatest { combine(observe(ledgerId), accounts(ledgerId, true)) { goals, rows -> SavingsUiState(loading = false, goals = goals, accounts = rows) }.catch { emit(SavingsUiState(loading = false, failed = true)) } }
    val uiState = combine(base, local) { data, form -> data.copy(selectedId = form.selectedId, showArchived = form.showArchived, editor = form.editor, movement = form.movement, busy = form.busy, writeFailed = form.writeFailed) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), SavingsUiState())
    fun onAction(action: SavingsAction) {
        if (local.value.busy) return
        when (action) {
            SavingsAction.Retry -> retry.value++
            SavingsAction.Create -> local.update { it.copy(editor = GoalEditor(accountId = uiState.value.availableAccounts.firstOrNull()?.id.orEmpty()), writeFailed = false) }
            is SavingsAction.Select -> local.update { it.copy(selectedId = if (it.selectedId == action.id) null else action.id) }
            is SavingsAction.Edit -> {
                val progress = uiState.value.goals.find { it.goal.id == action.id } ?: return
                val goal = progress.goal
                local.update { it.copy(editor = GoalEditor(goal.id, goal.name, goal.target.toPlainString(), goal.accountId, goal.deadline?.toString().orEmpty(), progress.hasLinks), writeFailed = false) }
            }
            is SavingsAction.EditorChanged -> local.update { it.copy(editor = action.editor, writeFailed = false) }
            SavingsAction.Dismiss -> local.update { it.copy(editor = null, movement = null, writeFailed = false) }
            SavingsAction.ToggleArchived -> local.update { it.copy(showArchived = !it.showArchived) }
            SavingsAction.Save -> {
                val form = uiState.value.editor?.takeIf { it.valid } ?: return
                write { save(form.id, ledgerId, form.accountId, form.name, requireNotNull(form.amount), form.date) }
            }
            is SavingsAction.Archive -> write { save.archive(action.id, action.archived) }
            is SavingsAction.Move -> {
                val goal = uiState.value.goals.find { it.goal.id == action.id }?.goal ?: return
                local.update { it.copy(movement = SavingsMovement(goal.id, action.deposit, uiState.value.availableAccounts.firstOrNull { it.id != goal.accountId }?.id.orEmpty()), writeFailed = false) }
            }
            is SavingsAction.MovementChanged -> local.update { it.copy(movement = action.movement, writeFailed = false) }
            is SavingsAction.ConfirmMovement -> {
                val form = uiState.value.movement ?: return
                val amount = Money.parse(form.amount)?.takeIf { it.cents > 0 } ?: return
                write { move(form.goalId, form.otherAccountId, amount, form.deposit, action.title) }
            }
        }
    }
    private fun write(block: suspend () -> AppResult<*>) {
        local.update { it.copy(busy = true, writeFailed = false) }
        viewModelScope.launch {
            try {
                val success = block().isSuccess
                local.update { it.copy(busy = false, writeFailed = !success, editor = if (success) null else it.editor, movement = if (success) null else it.movement) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { local.update { it.copy(busy = false, writeFailed = true) } }
        }
    }
}
