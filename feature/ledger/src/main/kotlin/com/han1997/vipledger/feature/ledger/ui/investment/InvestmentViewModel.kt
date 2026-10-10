package com.han1997.vipledger.feature.ledger.ui.investment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.investment.CloseInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.investment.CreateInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.investment.InvestMoreUseCase
import com.han1997.vipledger.core.domain.usecase.investment.ObserveInvestmentsUseCase
import com.han1997.vipledger.core.domain.usecase.investment.RecordInvestmentResultUseCase
import com.han1997.vipledger.core.domain.usecase.investment.RedeemInvestmentUseCase
import com.han1997.vipledger.core.model.InvestmentLinkKind
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

/** 理财台账：新建、追加投入、赎回本金、登记收益/损失、结束与恢复。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class InvestmentViewModel @Inject constructor(
    saved: SavedStateHandle,
    observe: ObserveInvestmentsUseCase,
    accounts: ObserveAccountsUseCase,
    private val create: CreateInvestmentUseCase,
    private val investMore: InvestMoreUseCase,
    private val redeem: RedeemInvestmentUseCase,
    private val result: RecordInvestmentResultUseCase,
    private val close: CloseInvestmentUseCase,
) : ViewModel() {

    private val ledgerId: String = saved.get<String>("ledgerId").orEmpty()
    private val local = MutableStateFlow(InvestmentUiState())
    private val retry = MutableStateFlow(0)
    private val base = retry.flatMapLatest {
        combine(observe(ledgerId), accounts(ledgerId, true)) { investments, rows ->
            InvestmentUiState(loading = false, investments = investments, accounts = rows)
        }.catch { emit(InvestmentUiState(loading = false, failed = true)) }
    }
    val uiState = combine(base, local) { data, form ->
        data.copy(
            showClosed = form.showClosed,
            editor = form.editor,
            operation = form.operation,
            busy = form.busy,
            writeFailed = form.writeFailed,
            errorKey = form.errorKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), InvestmentUiState())

    fun onAction(action: InvestmentAction) {
        if (local.value.busy) return
        when (action) {
            InvestmentAction.Retry -> retry.value++
            InvestmentAction.ToggleClosed -> local.update { it.copy(showClosed = !it.showClosed) }
            InvestmentAction.Dismiss -> local.update {
                it.copy(editor = null, operation = null, writeFailed = false, errorKey = null)
            }
            InvestmentAction.Create -> local.update {
                it.copy(
                    editor = InvestmentEditor(cashAccountId = uiState.value.availableAccounts.firstOrNull()?.id.orEmpty()),
                    writeFailed = false,
                    errorKey = null,
                )
            }
            is InvestmentAction.EditorChanged -> local.update {
                it.copy(editor = action.editor, writeFailed = false, errorKey = null)
            }
            InvestmentAction.Save -> {
                val editor = uiState.value.editor?.takeIf { it.valid } ?: return
                write { create(ledgerId, editor.name, editor.cashAccountId, editor.money!!, editor.remark, null, "") }
            }
            is InvestmentAction.Operate -> openOperation(action.id, action.kind)
            is InvestmentAction.OperationChanged -> local.update {
                it.copy(operation = action.form, writeFailed = false, errorKey = null)
            }
            is InvestmentAction.ConfirmOperation -> {
                val form = uiState.value.operation ?: return
                val amount = form.money ?: return
                write {
                    when (form.kind) {
                        InvestmentLinkKind.INVEST -> investMore(form.investmentId, form.cashAccountId, amount, null, action.title)
                        InvestmentLinkKind.REDEEM -> redeem(form.investmentId, form.cashAccountId, amount, null, action.title)
                        else -> result(form.investmentId, form.cashAccountId, amount, form.kind, null, action.title)
                    }
                }
            }
            is InvestmentAction.SetClosed -> write { close(action.id, action.closed) }
        }
    }

    private fun openOperation(investmentId: String, kind: InvestmentLinkKind) {
        val progress = uiState.value.investments.firstOrNull { it.investment.id == investmentId } ?: return
        local.update {
            it.copy(
                operation = InvestmentOperation(
                    investmentId = investmentId,
                    kind = kind,
                    cashAccountId = uiState.value.availableAccounts.firstOrNull()?.id.orEmpty(),
                    amount = if (kind == InvestmentLinkKind.REDEEM) {
                        progress.summary.principalOutstanding.toPlainString()
                    } else {
                        ""
                    },
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
                        operation = if (success) null else it.operation,
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