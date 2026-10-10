package com.han1997.vipledger.feature.ledger.ui.loan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.result.getOrNull
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.loan.ChangeInstallmentRateInput
import com.han1997.vipledger.core.domain.usecase.loan.ChangeInstallmentRateUseCase
import com.han1997.vipledger.core.domain.usecase.loan.CorrectLegacyInstallmentRepaymentUseCase
import com.han1997.vipledger.core.domain.usecase.loan.GetInstallmentPlanDetailUseCase
import com.han1997.vipledger.core.domain.usecase.loan.PrepayInstallmentPlanInput
import com.han1997.vipledger.core.domain.usecase.loan.PrepayInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.loan.PreviewLegacyInstallmentRepaymentsUseCase
import com.han1997.vipledger.core.domain.usecase.loan.RepayInstallmentPeriodInput
import com.han1997.vipledger.core.domain.usecase.loan.RepayInstallmentPeriodUseCase
import com.han1997.vipledger.core.domain.usecase.loan.SettleInstallmentPlanInput
import com.han1997.vipledger.core.domain.usecase.loan.SettleInstallmentPlanUseCase
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.common.failureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 分期详情：读计划与各期，支持逐期核销、提前结清、部分提前还款、利率变更与旧口径修正。 */
@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveAccountsUseCase,
    private val getDetail: GetInstallmentPlanDetailUseCase,
    private val repayPeriod: RepayInstallmentPeriodUseCase,
    private val settlePlan: SettleInstallmentPlanUseCase,
    private val prepayPlan: PrepayInstallmentPlanUseCase,
    private val changeRate: ChangeInstallmentRateUseCase,
    private val previewLegacy: PreviewLegacyInstallmentRepaymentsUseCase,
    private val correctLegacy: CorrectLegacyInstallmentRepaymentUseCase,
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
                it.copy(repayPeriodNo = action.periodNo, repayAmountInput = "")
            }

            is LoanDetailAction.SelectRepayAccount -> _uiState.update {
                it.copy(repayAccountId = action.accountId)
            }

            is LoanDetailAction.UpdateRepayAmount -> _uiState.update {
                it.copy(repayAmountInput = action.text)
            }

            LoanDetailAction.ConfirmRepay -> repay()
            LoanDetailAction.CancelRepay -> _uiState.update {
                it.copy(repayPeriodNo = null, repayAmountInput = "")
            }

            LoanDetailAction.OpenSettle -> _uiState.update { it.copy(settleOpen = true) }
            LoanDetailAction.CancelSettle -> _uiState.update { it.copy(settleOpen = false) }
            LoanDetailAction.ConfirmSettle -> settle()

            LoanDetailAction.OpenPrepay -> _uiState.update {
                it.copy(prepayOpen = true, prepayAmountInput = "")
            }

            LoanDetailAction.CancelPrepay -> _uiState.update {
                it.copy(prepayOpen = false, prepayAmountInput = "")
            }

            is LoanDetailAction.UpdatePrepayAmount -> _uiState.update {
                it.copy(prepayAmountInput = action.text)
            }

            LoanDetailAction.ConfirmPrepay -> prepay()

            LoanDetailAction.OpenRate -> rateDefaults()?.let { defaults ->
                _uiState.update {
                    it.copy(
                        rateOpen = true,
                        rateFromPeriodInput = it.rateFromPeriodInput.ifBlank { defaults.first },
                        rateValueInput = it.rateValueInput.ifBlank { defaults.second },
                    )
                }
            } ?: Unit

            LoanDetailAction.CancelRate -> _uiState.update { it.copy(rateOpen = false) }

            is LoanDetailAction.UpdateRateFrom -> _uiState.update {
                it.copy(rateFromPeriodInput = action.text)
            }

            is LoanDetailAction.UpdateRateValue -> _uiState.update {
                it.copy(rateValueInput = action.text)
            }

            LoanDetailAction.ConfirmRate -> changeRateValue()

            LoanDetailAction.OpenLegacy -> _uiState.update { it.copy(legacyOpen = true) }
            LoanDetailAction.CancelLegacy -> _uiState.update { it.copy(legacyOpen = false) }
            LoanDetailAction.ConfirmLegacy -> correctLegacyRepayments()

            LoanDetailAction.ConsumeNotice -> _uiState.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val detail = getDetail(planId)
            val legacy = previewLegacy(planId)
            _uiState.update { state ->
                val firstUntouched = detail?.periods?.firstOrNull { it.isUntouched }?.periodNo
                state.copy(
                    isLoading = false,
                    detail = detail,
                    legacy = legacy,
                    rateFromPeriodInput = state.rateFromPeriodInput.ifBlank {
                        firstUntouched?.toString().orEmpty()
                    },
                    rateValueInput = state.rateValueInput.ifBlank {
                        detail?.plan?.let { percentText(it.annualRateBp) }.orEmpty()
                    },
                )
            }
        }
    }

    private fun rateDefaults(): Pair<String, String>? {
        val state = _uiState.value
        val from = state.untouchedPeriods.firstOrNull()?.periodNo ?: return null
        return from.toString() to percentText(state.detail?.plan?.annualRateBp ?: 0)
    }

    private fun repay() {
        val state = _uiState.value
        val periodNo = state.repayPeriodNo ?: return
        if (state.repayAmountInvalid()) return
        val accountId = state.repayAccountId ?: return
        val amount = state.repayAmountValue()
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = repayPeriod(
                RepayInstallmentPeriodInput(
                    planId = planId,
                    periodNo = periodNo,
                    fromAccountId = accountId,
                    amount = amount,
                ),
            )
            finish(result.isSuccess, LoanNotice.REPAID, result.failureMessage()) {
                it.copy(repayPeriodNo = null, repayAmountInput = "")
            }
        }
    }

    private fun settle() {
        val accountId = _uiState.value.repayAccountId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = settlePlan(SettleInstallmentPlanInput(planId = planId, fromAccountId = accountId))
            finish(result.isSuccess, LoanNotice.SETTLED, result.failureMessage()) { it.copy(settleOpen = false) }
        }
    }

    private fun prepay() {
        val state = _uiState.value
        val amount = state.prepayAmountValue() ?: return
        if (state.prepayAmountInvalid()) return
        val accountId = state.repayAccountId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = prepayPlan(
                PrepayInstallmentPlanInput(
                    planId = planId,
                    fromAccountId = accountId,
                    amount = amount,
                ),
            )
            finish(result.isSuccess, LoanNotice.PREPAID, result.failureMessage()) {
                it.copy(prepayOpen = false, prepayAmountInput = "")
            }
        }
    }

    private fun changeRateValue() {
        val state = _uiState.value
        if (state.rateFromPeriodInvalid()) return
        val fromPeriod = state.rateFromPeriodValue() ?: return
        val rateBp = state.rateValueBp() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = changeRate(
                ChangeInstallmentRateInput(planId = planId, fromPeriodNo = fromPeriod, annualRateBp = rateBp),
            )
            finish(result.isSuccess, LoanNotice.RATE_CHANGED, result.failureMessage()) {
                it.copy(rateOpen = false)
            }
        }
    }

    private fun correctLegacyRepayments() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = correctLegacy(planId)
            val corrected = result.getOrNull() ?: 0
            _uiState.update {
                it.copy(
                    busy = false,
                    legacyOpen = false,
                    notice = if (result.isSuccess) LoanNotice.LEGACY_CORRECTED else LoanNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                    legacyCorrectedCount = corrected,
                )
            }
            if (result.isSuccess) load()
        }
    }

    private fun finish(
        success: Boolean,
        notice: LoanNotice,
        failure: String?,
        reset: (LoanDetailUiState) -> LoanDetailUiState,
    ) {
        _uiState.update {
            reset(it).copy(
                busy = false,
                notice = if (success) notice else LoanNotice.FAILED,
                noticeDetail = failure,
            )
        }
        if (success) load()
    }

    private fun percentText(bp: Int): String =
        if (bp % 100 == 0) (bp / 100).toString() else (bp / 100.0).toString()
}
