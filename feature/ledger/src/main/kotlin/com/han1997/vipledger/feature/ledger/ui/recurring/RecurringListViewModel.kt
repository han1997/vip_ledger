package com.han1997.vipledger.feature.ledger.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.DeleteRecurringRuleUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.GenerateRecurringTxnsUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.ObserveRecurringRulesUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.SetRecurringRuleEnabledUseCase
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.common.failureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 周期规则列表：启停、删除、「立即生成」手动跑一次引擎。 */
@HiltViewModel
class RecurringListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRules: ObserveRecurringRulesUseCase,
    observeAccounts: ObserveAccountsUseCase,
    private val generateNow: GenerateRecurringTxnsUseCase,
    private val setEnabled: SetRecurringRuleEnabledUseCase,
    private val deleteRule: DeleteRecurringRuleUseCase,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()

    private val local = MutableStateFlow(RecurringListLocal())

    val uiState: StateFlow<RecurringListUiState> = combine(
        observeRules(ledgerId),
        observeAccounts(ledgerId, includeArchived = true),
        local,
    ) { rules, accounts, local ->
        RecurringListUiState(
            isLoading = false,
            ledgerId = ledgerId,
            rules = rules,
            accountNames = accounts.accountNameMap(),
            generating = local.generating,
            pendingDelete = local.pendingDelete,
            notice = local.notice,
            noticeCount = local.noticeCount,
            noticeDetail = local.noticeDetail,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringListUiState())

    fun onAction(action: RecurringListAction) {
        when (action) {
            RecurringListAction.GenerateNow -> generate()
            is RecurringListAction.ToggleEnabled -> execute(
                success = if (action.rule.enabled) RecurringNotice.PAUSED else RecurringNotice.ENABLED,
                block = { setEnabled(action.rule.id, !action.rule.enabled) },
            )
            is RecurringListAction.RequestDelete -> local.update { it.copy(pendingDelete = action.rule) }
            RecurringListAction.ConfirmDelete -> confirmDelete()
            RecurringListAction.DismissDelete -> local.update { it.copy(pendingDelete = null) }
            RecurringListAction.ConsumeNotice -> local.update {
                it.copy(notice = null, noticeCount = null, noticeDetail = null)
            }
        }
    }

    private fun generate() {
        if (local.value.generating) return
        local.update { it.copy(generating = true) }
        viewModelScope.launch {
            val result = generateNow(ledgerId)
            local.update { state ->
                val report = (result as? AppResult.Success)?.value
                state.copy(
                    generating = false,
                    notice = when {
                        report == null -> RecurringNotice.FAILED
                        report.generated > 0 -> RecurringNotice.GENERATED
                        else -> RecurringNotice.NOTHING_DUE
                    },
                    noticeCount = report?.generated,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private fun confirmDelete() {
        val rule = local.value.pendingDelete ?: return
        viewModelScope.launch {
            val result = deleteRule(rule.id)
            local.update {
                it.copy(
                    pendingDelete = null,
                    notice = if (result.isSuccess) RecurringNotice.DELETED else RecurringNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private fun execute(
        success: RecurringNotice,
        block: suspend () -> AppResult<Unit>,
    ) {
        viewModelScope.launch {
            val result = block()
            local.update {
                it.copy(
                    notice = if (result.isSuccess) success else RecurringNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private data class RecurringListLocal(
        val generating: Boolean = false,
        val pendingDelete: RecurringRule? = null,
        val notice: RecurringNotice? = null,
        val noticeCount: Int? = null,
        val noticeDetail: String? = null,
    )
}
