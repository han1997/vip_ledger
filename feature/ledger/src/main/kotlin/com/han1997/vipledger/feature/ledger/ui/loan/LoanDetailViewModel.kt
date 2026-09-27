package com.han1997.vipledger.feature.ledger.ui.loan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.loan.GetInstallmentPlanDetailUseCase
import com.han1997.vipledger.core.domain.usecase.loan.RepayInstallmentPeriodInput
import com.han1997.vipledger.core.domain.usecase.loan.RepayInstallmentPeriodUseCase
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.common.failureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 分期详情：读计划与各期，逐期还款核销。 */
@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveAccountsUseCase,
    private val getDetail: GetInstallmentPlanDetailUseCase,
    private val repayPeriod: RepayInstallmentPeriodUseCase,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()
    private val planId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_PLAN_ID).orEmpty()

    private val _uiState = MutableStateFlow(
        LoanDetailUiState(planId = planId, ledgerId = ledgerId),
    )
    val uiState: StateFlow<LoanDetailUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            observeAccounts(ledgerId, includeArchived = false).collect { accounts ->
                _uiState.update { state ->
                    val debtAccountId = state.detail?.plan?.accountId
                    val repayAccounts = accounts.filter { it.id != debtAccountId }
                    val current = state.repayAccountId
                        ?.takeIf { id -> repayAccounts.any { it.id == id } }
                    state.copy(
                        accountNames = accounts.accountNameMap(),
                        repayAccounts = repayAccounts,
                        repayAccountId = current ?: repayAccounts.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    fun onAction(action: LoanDetailAction) {
        when (action) {
            is LoanDetailAction.StartRepay -> _uiState.update {
                it.copy(repayPeriodNo = action.periodNo)
            }

            is LoanDetailAction.SelectRepayAccount -> _uiState.update {
                it.copy(repayAccountId = action.accountId)
            }

            LoanDetailAction.ConfirmRepay -> repay()
            LoanDetailAction.CancelRepay -> _uiState.update { it.copy(repayPeriodNo = null) }
            LoanDetailAction.ConsumeNotice -> _uiState.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val detail = getDetail(planId)
            _uiState.update { it.copy(isLoading = false, detail = detail) }
        }
    }

    private fun repay() {
        val state = _uiState.value
        val periodNo = state.repayPeriodNo ?: return
        val accountId = state.repayAccountId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(repaying = true) }
            val result = repayPeriod(
                RepayInstallmentPeriodInput(
                    planId = planId,
                    periodNo = periodNo,
                    fromAccountId = accountId,
                ),
            )
            _uiState.update {
                it.copy(
                    repaying = false,
                    repayPeriodNo = null,
                    notice = if (result.isSuccess) LoanNotice.REPAID else LoanNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
            if (result.isSuccess) load()
        }
    }
}
