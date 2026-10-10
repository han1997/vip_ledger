package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.format.tagNameMap
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.usecase.wealth.ObserveAssetsUseCase
import com.han1997.vipledger.core.domain.usecase.wealth.AdjustAccountBalanceUseCase
import com.han1997.vipledger.core.domain.usecase.account.ArchiveAccountUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ObserveTxnListUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountDetailViewModel @Inject constructor(saved: SavedStateHandle, assets: ObserveAssetsUseCase, txns: ObserveTxnListUseCase, categories: ObserveCategoryTreeUseCase, tags: ObserveTagsUseCase, private val adjust: AdjustAccountBalanceUseCase, private val archive: ArchiveAccountUseCase) : ViewModel() {
    private val ledgerId = saved.get<String>("ledgerId").orEmpty()
    private val accountId = saved.get<String>("accountId").orEmpty()
    private val local = MutableStateFlow(AccountDetailUiState())
    private val retry = MutableStateFlow(0)
    private val base = retry.flatMapLatest {
        combine(assets(ledgerId), txns(TxnFilter(ledgerId = ledgerId, accountIds = setOf(accountId), draftStatus = DraftStatus.CONFIRMED)), categories(ledgerId, true), tags(ledgerId)) { report, entries, cats, labels ->
            AccountDetailUiState(loading = false, account = report.accounts.find { it.id == accountId }, txns = entries, accountNames = report.accounts.accountNameMap(), categoryNames = cats.categoryNameMap(), tagNames = labels.tagNameMap(), baseline = report.baselines.find { it.accountId == accountId })
        }.catch { emit(AccountDetailUiState(loading = false, failed = true)) }
    }
    val uiState = combine(base, local) { state, form -> state.copy(balanceInput = form.balanceInput, busy = form.busy, writeFailed = form.writeFailed) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), AccountDetailUiState())
    fun onAction(action: AccountDetailAction) {
        if (local.value.busy) return
        when (action) {
            AccountDetailAction.Retry -> retry.value++
            AccountDetailAction.Adjust -> local.update { it.copy(balanceInput = uiState.value.account?.currentBalance?.toPlainString(), writeFailed = false) }
            is AccountDetailAction.Input -> local.update { it.copy(balanceInput = action.text) }
            AccountDetailAction.Dismiss -> local.update { it.copy(balanceInput = null, writeFailed = false) }
            is AccountDetailAction.Archive -> write { archive(accountId, action.archived) }
            is AccountDetailAction.SaveAdjustment -> {
                val value = uiState.value.balanceInput?.let(Money::parse) ?: return
                write { adjust(accountId, value, action.title) }
            }
        }
    }
    private fun write(block: suspend () -> AppResult<*>) {
        local.update { it.copy(busy = true, writeFailed = false) }
        viewModelScope.launch {
            try {
                val success = block().isSuccess
                local.update { it.copy(busy = false, writeFailed = !success, balanceInput = if (success) null else it.balanceInput) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { local.update { it.copy(busy = false, writeFailed = true) } }
        }
    }
}
