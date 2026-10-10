package com.han1997.vipledger.feature.ledger.ui.reimburse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.CloseReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ObserveReimbursementsUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ReceiveReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.SaveReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ObserveTxnListUseCase
import com.han1997.vipledger.core.model.TransactionType
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
class ReimbursementViewModel @Inject constructor(
    saved: SavedStateHandle,
    observe: ObserveReimbursementsUseCase,
    observeTxns: ObserveTxnListUseCase,
    accounts: ObserveAccountsUseCase,
    private val save: SaveReimbursementUseCase,
    private val receive: ReceiveReimbursementUseCase,
    private val close: CloseReimbursementUseCase,
) : ViewModel() {
    private val ledgerId: String = saved.get<String>("ledgerId").orEmpty()
    private val local = MutableStateFlow(ReimbursementUiState())
    private val retry = MutableStateFlow(0)
    private val base = retry.flatMapLatest {
        combine(
            observe(ledgerId),
            observeTxns(TxnFilter(ledgerId = ledgerId, types = setOf(TransactionType.EXPENSE))),
            accounts(ledgerId, true),
        ) { projects, txns, rows -> ReimbursementUiState(loading = false, projects = projects, expenses = txns, accounts = rows) }
            .catch { emit(ReimbursementUiState(loading = false, failed = true)) }
    }
    val uiState = combine(base, local) { data, form ->
        data.copy(
            selectedId = form.selectedId,
            showClosed = form.showClosed,
            editor = form.editor,
            receipt = form.receipt,
            busy = form.busy,
            writeFailed = form.writeFailed,
            errorKey = form.errorKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), ReimbursementUiState())

    fun onAction(action: ReimbursementAction) {
        if (local.value.busy) return
        when (action) {
            ReimbursementAction.Retry -> retry.value++
            ReimbursementAction.ToggleClosed -> local.update { it.copy(showClosed = !it.showClosed) }
            ReimbursementAction.Dismiss -> local.update { it.copy(editor = null, receipt = null, writeFailed = false, errorKey = null) }
            ReimbursementAction.Create -> local.update { it.copy(editor = ReimbursementEditor(), writeFailed = false, errorKey = null) }
            is ReimbursementAction.Select -> local.update { it.copy(selectedId = if (it.selectedId == action.id) null else action.id) }
            is ReimbursementAction.Edit -> {
                val progress = uiState.value.projects.firstOrNull { it.project.id == action.id } ?: return
                local.update {
                    it.copy(
                        editor = ReimbursementEditor(
                            id = progress.project.id,
                            title = progress.project.title,
                            counterparty = progress.project.counterparty,
                            remark = progress.project.remark,
                            itemIds = progress.items.map { item -> item.id }.toSet(),
                        ),
                        writeFailed = false,
                        errorKey = null,
                    )
                }
            }
            is ReimbursementAction.EditorChanged -> local.update { it.copy(editor = action.editor, writeFailed = false, errorKey = null) }
            is ReimbursementAction.ToggleItem -> local.update { snapshot ->
                val editor = snapshot.editor ?: return@update snapshot
                val ids = if (action.txnId in editor.itemIds) editor.itemIds - action.txnId else editor.itemIds + action.txnId
                snapshot.copy(editor = editor.copy(itemIds = ids), writeFailed = false, errorKey = null)
            }
            ReimbursementAction.Save -> {
                val editor = uiState.value.editor?.takeIf { it.valid } ?: return
                write { save(editor.id, ledgerId, editor.title, editor.counterparty, editor.remark, editor.itemIds.toList()) }
            }
            is ReimbursementAction.Receive -> {
                val progress = uiState.value.projects.firstOrNull { it.project.id == action.projectId }
                val item = progress?.items?.firstOrNull { it.id == action.itemTxnId }
                val remaining = item?.let { com.han1997.vipledger.core.domain.model.ReimbursementCalculator.remainingFor(it, progress.receipts.filter { receipt -> receipt.refundOfTxnId == it.id }) }
                local.update {
                    it.copy(
                        receipt = ReimbursementReceiptForm(
                            projectId = action.projectId,
                            itemTxnId = action.itemTxnId,
                            accountId = uiState.value.availableAccounts.firstOrNull()?.id.orEmpty(),
                            amount = remaining?.toPlainString().orEmpty(),
                        ),
                        writeFailed = false,
                        errorKey = null,
                    )
                }
            }
            is ReimbursementAction.ReceiptChanged -> local.update { it.copy(receipt = action.form, writeFailed = false, errorKey = null) }
            is ReimbursementAction.ConfirmReceipt -> {
                val form = uiState.value.receipt ?: return
                val amount = form.money ?: return
                write { receive(form.projectId, form.itemTxnId, amount, form.accountId, null, action.title) }
            }
            is ReimbursementAction.SetClosed -> write { close(action.id, action.closed) }
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
                        receipt = if (success) null else it.receipt,
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
