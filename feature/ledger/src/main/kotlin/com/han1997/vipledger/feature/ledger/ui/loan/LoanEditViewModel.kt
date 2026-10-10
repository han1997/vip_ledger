package com.han1997.vipledger.feature.ledger.ui.loan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanInput
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanUseCase
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.common.failureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 新建分期计划：表单校验 + 调用保存用例。 */
@HiltViewModel
class LoanEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveAccountsUseCase,
    private val savePlan: SaveInstallmentPlanUseCase,
    timeProvider: TimeProvider,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()

    private val _uiState = MutableStateFlow(
        LoanEditUiState(
            ledgerId = ledgerId,
            firstDueDate = timeProvider.nowMillis().toLocalDate(timeProvider.zone),
        ),
    )
    val uiState: StateFlow<LoanEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAccounts(ledgerId, includeArchived = false).collect { accounts ->
                _uiState.update { state ->
                    val debt = accounts.filter { it.type.isDebt }
                    val current = state.accountId?.takeIf { id -> debt.any { it.id == id } }
                    val accountId = current ?: debt.firstOrNull()?.id
                    val cash = accounts.filter { it.isUsable && !it.isDebt }
                    val disbursement = state.disbursementAccountId
                        ?.takeIf { id -> cash.any { it.id == id && it.id != accountId } }
                        ?: cash.firstOrNull { it.id != accountId }?.id
                    state.copy(
                        isLoading = false,
                        accounts = accounts,
                        accountId = accountId,
                        disbursementAccountId = disbursement,
                    )
                }
            }
        }
    }

    fun onAction(action: LoanEditAction) {
        when (action) {
            is LoanEditAction.UpdateName -> update { it.copy(name = action.text) }
            is LoanEditAction.SelectAccount -> update { it.copy(accountId = action.accountId) }
            is LoanEditAction.SelectMethod -> update { it.copy(method = action.method) }
            is LoanEditAction.UpdatePrincipal -> update { it.copy(principalInput = action.text) }
            is LoanEditAction.UpdatePeriods -> update { it.copy(periodsInput = action.text) }
            is LoanEditAction.UpdateAnnualRate -> update { it.copy(annualRateInput = action.text) }
            is LoanEditAction.UpdateUpfrontFee -> update { it.copy(upfrontFeeInput = action.text) }
            is LoanEditAction.UpdatePerPeriodFee -> update { it.copy(perPeriodFeeInput = action.text) }
            is LoanEditAction.UpdateCustomTotals -> update { it.copy(customTotalsInput = action.text) }
            is LoanEditAction.SelectOrigin -> update { it.copy(origin = action.origin) }
            is LoanEditAction.SelectDisbursementAccount -> update {
                it.copy(disbursementAccountId = action.accountId)
            }
            is LoanEditAction.SelectFirstDueDate -> update { it.copy(firstDueDate = action.date) }
            is LoanEditAction.UpdateDueDay -> update { it.copy(dueDayInput = action.text) }
            is LoanEditAction.UpdateRemark -> update { it.copy(remark = action.text) }
            LoanEditAction.Save -> save()
            LoanEditAction.ConsumeError -> update { it.copy(error = null, saveFailureMessage = null) }
        }
    }

    private fun update(reducer: (LoanEditUiState) -> LoanEditUiState) {
        _uiState.update { reducer(it).copy(error = null) }
    }

    private fun save() {
        val state = _uiState.value
        val error = state.validationError()
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        val customAmounts = state.customAmountsValue()
        val input = SaveInstallmentPlanInput(
            ledgerId = ledgerId,
            accountId = state.accountId.orEmpty(),
            name = state.name.trim(),
            method = state.method,
            principal = state.principalValue()!!,
            periods = state.periodsValue()!!,
            firstDueDate = state.firstDueDate,
            annualRateBp = state.annualRateBpValue() ?: 0,
            dueDay = state.dueDayValue(),
            upfrontFee = state.upfrontFeeValue() ?: Money.ZERO,
            perPeriodFee = state.perPeriodFeeValue() ?: Money.ZERO,
            remark = state.remark.trim().takeIf { it.isNotEmpty() },
            origin = state.origin,
            disbursementAccountId = state.disbursementAccountId.takeIf { state.showsDisbursementAccount },
            customPeriodTotals = state.customTotalsValue().takeIf { customAmounts == null },
            customAmounts = customAmounts,
        )
        viewModelScope.launch {
            val result = savePlan(input)
            if (result.isSuccess) {
                _uiState.update { it.copy(saved = true) }
            } else {
                _uiState.update {
                    it.copy(
                        error = LoanEditError.SAVE_FAILED,
                        saveFailureMessage = result.failureMessage(),
                    )
                }
            }
        }
    }
}