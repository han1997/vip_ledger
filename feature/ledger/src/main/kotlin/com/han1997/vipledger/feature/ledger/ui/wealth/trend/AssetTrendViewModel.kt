package com.han1997.vipledger.feature.ledger.ui.wealth.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.AssetHistoryCalculator
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.ObserveAssetsUseCase
import com.han1997.vipledger.core.model.time.toLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * 资产趋势页（P7）：基线 + 有效账目 → [AssetHistoryCalculator] 逐日现算。
 * 数据源与资产页同源（[ObserveAssetsUseCase]），保证曲线终点与资产页数字一致。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetTrendViewModel @Inject constructor(
    private val timeProvider: TimeProvider,
    ledger: ObserveCurrentLedgerUseCase,
    assets: ObserveAssetsUseCase,
    txns: TxnRepository,
) : ViewModel() {

    private val range = MutableStateFlow(TrendRange.LAST_90_DAYS)
    private val retry = MutableStateFlow(0)

    private val base = combine(ledger(), retry) { current, _ -> current }.flatMapLatest { current ->
        if (current == null) flowOf(AssetTrendUiState(loading = false))
        else combine(
            assets(current.id),
            txns.observe(TxnFilter(current.id)),
            range,
        ) { report, txnList, selected ->
            val zone = timeProvider.zone
            val today = timeProvider.nowMillis().toLocalDate(zone)
            val earliestBaselineDay = report.baselines.minOfOrNull { it.capturedAt.toLocalDate(zone) }
            val points = AssetHistoryCalculator.history(
                accounts = report.accounts,
                baselines = report.baselines,
                txns = txnList,
                range = trendDateRangeOf(selected, today, earliestBaselineDay),
                zone = zone,
            )
            AssetTrendUiState(
                loading = false,
                ledgerId = current.id,
                ledgerName = current.name,
                range = selected,
                points = points,
                today = today,
            )
        }
            .onStart { emit(AssetTrendUiState()) }
            .catch { emit(AssetTrendUiState(loading = false, failed = true)) }
    }

    val uiState = base.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), AssetTrendUiState())

    fun onAction(action: AssetTrendAction) {
        when (action) {
            is AssetTrendAction.SetRange -> range.value = action.value
            AssetTrendAction.Retry -> retry.value++
        }
    }
}