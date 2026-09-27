package com.han1997.vipledger.feature.ledger.ui.drafts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.format.tagNameMap
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.recurring.ObservePendingDraftsUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ConfirmDraftUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DiscardDraftUseCase
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import com.han1997.vipledger.feature.ledger.ui.common.failureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 待确认草稿：确认入账 / 丢弃 / 批量确认。 */
@HiltViewModel
class DraftListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePendingDrafts: ObservePendingDraftsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    observeTags: ObserveTagsUseCase,
    private val confirmDraft: ConfirmDraftUseCase,
    private val discardDraft: DiscardDraftUseCase,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()

    private val _uiState = MutableStateFlow(DraftListUiState(ledgerId = ledgerId))
    val uiState: StateFlow<DraftListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePendingDrafts(ledgerId).collect { drafts ->
                _uiState.update { it.copy(isLoading = false, drafts = drafts) }
            }
        }
        viewModelScope.launch {
            observeAccounts(ledgerId, includeArchived = true).collect { accounts ->
                _uiState.update { it.copy(accountNames = accounts.accountNameMap()) }
            }
        }
        viewModelScope.launch {
            observeCategoryTree(ledgerId, includeArchived = true).collect { nodes ->
                _uiState.update { it.copy(categoryNames = nodes.categoryNameMap()) }
            }
        }
        viewModelScope.launch {
            observeTags(ledgerId).collect { tags ->
                _uiState.update { it.copy(tagNames = tags.tagNameMap()) }
            }
        }
    }

    fun onAction(action: DraftAction) {
        when (action) {
            is DraftAction.Confirm -> confirm(action.txnId)
            is DraftAction.Discard -> discard(action.txnId)
            DraftAction.ConfirmAll -> confirmAll()
            DraftAction.ConsumeNotice -> _uiState.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun confirm(txnId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyIds = it.busyIds + txnId) }
            val result = confirmDraft(txnId)
            _uiState.update { state ->
                state.copy(
                    busyIds = state.busyIds - txnId,
                    notice = if (result.isSuccess) DraftNotice.CONFIRMED else DraftNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private fun discard(txnId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyIds = it.busyIds + txnId) }
            val result = discardDraft(txnId)
            _uiState.update { state ->
                state.copy(
                    busyIds = state.busyIds - txnId,
                    notice = if (result.isSuccess) DraftNotice.DISCARDED else DraftNotice.FAILED,
                    noticeDetail = result.failureMessage(),
                )
            }
        }
    }

    private fun confirmAll() {
        val pending = _uiState.value.drafts
        if (pending.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(confirmingAll = true) }
            var failed: String? = null
            pending.forEach { txn ->
                val result = confirmDraft(txn.id)
                if (!result.isSuccess && failed == null) failed = result.failureMessage()
            }
            _uiState.update { state ->
                state.copy(
                    confirmingAll = false,
                    notice = if (failed == null) DraftNotice.CONFIRMED else DraftNotice.FAILED,
                    noticeDetail = failed,
                )
            }
        }
    }
}
