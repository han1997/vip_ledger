package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.domain.usecase.wealth.ObserveAssetsUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.lending.ObserveLendingsUseCase
import com.han1997.vipledger.core.domain.usecase.investment.ObserveInvestmentsUseCase
import com.han1997.vipledger.core.domain.usecase.loan.ObserveInstallmentSummariesUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ObserveReimbursementsUseCase
import com.han1997.vipledger.core.domain.model.ReimbursementCalculator
import com.han1997.vipledger.core.model.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetsViewModel @Inject constructor(
    ledger: ObserveCurrentLedgerUseCase,
    assets: ObserveAssetsUseCase,
    reimbursements: ObserveReimbursementsUseCase,
    lendings: ObserveLendingsUseCase,
    installments: ObserveInstallmentSummariesUseCase,
    investments: ObserveInvestmentsUseCase,
) : ViewModel() {
    private val archived = MutableStateFlow(false)
    private val retry = MutableStateFlow(0)
    private val base = combine(ledger(), retry) { current, _ -> current }.flatMapLatest { current ->
        if (current == null) flowOf(AssetsUiState(loading = false))
        else combine(
            assets(current.id),
            reimbursements(current.id),
            lendings(current.id),
            installments(current.id),
            investments(current.id),
        ) { report, projects, records, plans, investments ->
            AssetsUiState(
                loading = false,
                ledgerId = current.id,
                ledgerName = current.name,
                report = report,
                reimbursementPending = ReimbursementCalculator.pendingTotal(projects),
                lendingOutstanding = Money.sum(records.filter { it.active }.map { it.outstanding }),
                lendingActive = records.count { it.active },
                installmentOutstanding = Money.sum(plans.filter { !it.isClosed }.map { it.remainingAmount }),
                installmentActive = plans.count { !it.isClosed },
                investmentPrincipal = Money.sum(investments.filter { it.active }.map { it.summary.principalOutstanding }),
                investmentActive = investments.count { it.active },
            )
        }
            .onStart { emit(AssetsUiState()) }.catch { emit(AssetsUiState(loading = false, failed = true)) }
    }
    val uiState = combine(base, archived) { state, show -> state.copy(showArchived = show) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), AssetsUiState())
    fun onAction(action: AssetsAction) { when (action) { AssetsAction.ToggleArchived -> archived.value = !archived.value; AssetsAction.Retry -> retry.value++ } }
}