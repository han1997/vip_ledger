package com.han1997.vipledger.feature.ledger.ui.loan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.loan.DeleteInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.loan.ObserveInstallmentSummariesUseCase
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.common.failureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 分期计划列表：订阅计划 + 各期汇总，支持删除计划。 */
@HiltViewModel
class LoanListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSummaries: ObserveInstallmentSummariesUseCase,
    observeAccounts: ObserveAccountsUseCase,
    private val deletePlan: DeleteInstallmentPlanUseCase,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()

    private val _uiState = MutableStateFlow(LoanListUiState(ledgerId = ledgerId))
    val uiState: StateFlow<LoanListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSummaries(ledgerId).collect { summaries ->
                _uiState.update { it.copy(isLoading = false, summaries = summaries) }
            }
        }
        viewModelScope.launch {
            observeAccounts(ledgerId, includeArchived = true).collect { accounts ->
                _uiState.update { it.copy(accountNames = accounts.accountNameMap()) }
            }
        }
    }

    fun onAction(action: LoanListAction) {
        when (action) {
            is LoanListAction.Delete -> delete(action.planId)
            LoanListAction.ConsumeNotice -> _uiState.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun delete(planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = planId) }
            val result = deletePlan(planId)
            _uiState.update { state ->
                state.copy(
                    deletingId = null,
                    notice = if (result.isSuccess) LoanNotice.DELETED else LoanNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }
}
