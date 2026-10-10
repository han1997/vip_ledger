package com.han1997.vipledger.feature.ledger.ui.lending

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.lending.CloseLendingUseCase
import com.han1997.vipledger.core.domain.usecase.lending.CreateLendingUseCase
import com.han1997.vipledger.core.domain.usecase.lending.ObserveLendingsUseCase
import com.han1997.vipledger.core.domain.usecase.lending.RecordLendingInterestUseCase
import com.han1997.vipledger.core.domain.usecase.lending.SettleLendingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class LendingViewModel @Inject constructor(
    saved: SavedStateHandle,
    observe: ObserveLendingsUseCase,
    accounts: ObserveAccountsUseCase,
    private val create: CreateLendingUseCase,
    private val settle: SettleLendingUseCase,
    private val interest: RecordLendingInterestUseCase,
    private val close: CloseLendingUseCase,
) : ViewModel() {
    private val ledgerId: String = saved.get<String>("ledgerId").orEmpty()
    private val local = MutableStateFlow(LendingUiState())
    private val retry = MutableStateFlow(0)
    private val base = retry.flatMapLatest {
        combine(observe(ledgerId), accounts(ledgerId, true)) { lendings, rows ->
            LendingUiState(loading = false, lendings = lendings, accounts = rows)
        }.catch { emit(LendingUiState(loading = false, failed = true)) }
    }
    val uiState = combine(base, local) { data, form ->
        data.copy(
            selectedId = form.selectedId,
            showClosed = form.showClosed,
            editor = form.editor,
            settlement = form.settlement,
            busy = form.busy,
            writeFailed = form.writeFailed,
            errorKey = form.errorKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), LendingUiState())

    fun onAction(action: LendingAction) {
        if (local.value.busy) return
        when (action) {
            LendingAction.Retry -> retry.value++
            LendingAction.ToggleClosed -> local.update { it.copy(showClosed = !it.showClosed) }
            LendingAction.Dismiss -> local.update { it.copy(editor = null, settlement = null, writeFailed = false, errorKey = null) }
            LendingAction.Create -> local.update {
                it.copy(
                    editor = LendingEditor(cashAccountId = uiState.value.availableAccounts.firstOrNull()?.id.orEmpty()),
                    writeFailed = false,
                    errorKey = null,
                )
            }
            is LendingAction.Select -> local.update { it.copy(selectedId = if (it.selectedId == action.id) null else action.id) }
            is LendingAction.EditorChanged -> local.update { it.copy(editor = action.editor, writeFailed = false, errorKey = null) }
            LendingAction.Save -> {
                val editor = uiState.value.editor?.takeIf { it.valid } ?: return
                write { create(ledgerId, editor.direction, editor.counterparty, editor.amount!!, editor.cashAccountId, editor.date, editor.remark, null, "") }
            }
            is LendingAction.Settle -> openSettlement(action.id, interest = false)
            is LendingAction.Interest -> openSettlement(action.id, interest = true)
            is LendingAction.SettlementChanged -> local.update { it.copy(settlement = action.form, writeFailed = false, errorKey = null) }
            is LendingAction.ConfirmSettlement -> {
                val form = uiState.value.settlement ?: return
                val amount = form.money ?: return
                write {
                    if (form.interest) interest(form.lendingId, form.cashAccountId, amount, null, action.title)
                    else settle(form.lendingId, form.cashAccountId, amount, null, action.title)
                }
            }
            is LendingAction.SetClosed -> write { close(action.id, action.closed) }
        }
    }

    private fun openSettlement(lendingId: String, interest: Boolean) {
        val progress = uiState.value.lendings.firstOrNull { it.record.id == lendingId } ?: return
        local.update {
            it.copy(
                settlement = LendingSettlement(
                    lendingId = lendingId,
                    interest = interest,
                    cashAccountId = uiState.value.availableAccounts.firstOrNull()?.id.orEmpty(),
                    amount = if (interest) "" else progress.outstanding.toPlainString(),
                ),
                writeFailed = false,
                errorKey = null,
            )
        }
    }

    private fun write(block: suspend () -> AppResult<*>) {
        local.update { it.copy(busy = true, writeFailed = false, errorKey = null) }
        viewModelScope.launch {
            try {
                val result = block()
                val success = result.isSuccess
                val reason = (result as? AppResult.Failure)?.error?.message
                local.update {
                    it.copy(
                        busy = false,
                        writeFailed = !success,
                        errorKey = if (success) null else reason,
                        editor = if (success) null else it.editor,
                        settlement = if (success) null else it.settlement,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                local.update { it.copy(busy = false, writeFailed = true) }
            }
        }
    }
}
