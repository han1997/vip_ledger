package com.han1997.vipledger.feature.ledger.ui.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.usecase.budget.ObserveBudgetUseCase
import com.han1997.vipledger.core.domain.usecase.budget.SaveBudgetUseCase
import com.han1997.vipledger.core.domain.usecase.home.ObserveLocalDayUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.common.validCalendarDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    day: ObserveLocalDayUseCase,
    observe: ObserveBudgetUseCase,
    categories: ObserveCategoryTreeUseCase,
    private val save: SaveBudgetUseCase,
) : ViewModel() {
    private val ledgerId = savedState.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()
    private val selected = savedState.getStateFlow<String?>("budgetMonth", null)
    private val retry = MutableStateFlow(0)
    private val editor = MutableStateFlow<BudgetEditor?>(null)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)
    private val base = combine(day(), selected, retry) { local, chosen, _ ->
        DateRangePreset.startOfMonth(validCalendarDate(chosen) ?: local.date) to local.zone
    }.flatMapLatest { (month, zone) ->
        combine(observe(ledgerId, month, zone), categories(ledgerId, includeArchived = true)) { report, nodes ->
            val options = nodes.filter { it.category.kind == CategoryKind.EXPENSE }.flatMap { node ->
                listOf(BudgetOption(node.category.id, node.category.name)) + node.children.map { BudgetOption(it.id, "${node.category.name} / ${it.name}") }
            }
            BudgetUiState(loading = false, month = month, report = report, options = options)
        }.onStart { emit(BudgetUiState(month = month)) }
            .catch { emit(BudgetUiState(loading = false, failed = true, month = month)) }
    }
    val uiState = combine(base, editor, saving, saveFailed) { state, form, busy, failed -> state.copy(editor = form, saving = busy, saveFailed = failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), BudgetUiState())

    fun onAction(action: BudgetAction) {
        if (saving.value) return
        when (action) {
            is BudgetAction.MoveMonth -> {
                val next = uiState.value.month.plus(action.delta, DateTimeUnit.MONTH)
                if (next.year in 1900..9998) savedState["budgetMonth"] = next.toString()
            }
            BudgetAction.Today -> savedState["budgetMonth"] = null
            BudgetAction.Retry -> retry.value++
            is BudgetAction.Edit -> { saveFailed.value = false; edit(action.scopeKey) }
            is BudgetAction.SelectScope -> edit(action.scopeKey)
            is BudgetAction.Input -> { editor.value = editor.value?.copy(input = action.text); saveFailed.value = false }
            BudgetAction.Dismiss -> editor.value = null
            BudgetAction.Save, BudgetAction.Disable -> {
                val form = editor.value ?: return
                val amount = if (action == BudgetAction.Disable) null else form.amount ?: return
                val month = uiState.value.month
                saving.value = true
                viewModelScope.launch {
                    try {
                        val result = save(ledgerId, month, form.scopeKey, amount)
                        if (result.isSuccess) editor.value = null else saveFailed.value = true
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { saveFailed.value = true }
                    finally { saving.value = false }
                }
            }
        }
    }
    private fun edit(scope: String) {
        editor.value = BudgetEditor(scope, uiState.value.report?.progress?.find { it.limit.scopeKey == scope }?.limit?.amount?.toPlainString().orEmpty())
    }
}
