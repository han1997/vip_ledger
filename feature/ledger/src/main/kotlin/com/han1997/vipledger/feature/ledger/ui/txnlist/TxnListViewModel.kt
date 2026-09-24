package com.han1997.vipledger.feature.ledger.ui.txnlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.format.tagNameMap
import com.han1997.vipledger.core.domain.calculator.TxnDayGrouper
import com.han1997.vipledger.core.domain.model.TxnDayGroup
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveLedgersUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DeleteTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ObserveTxnListUseCase
import com.han1997.vipledger.feature.ledger.navigation.LedgerRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 明细列表：按天分组的账目 + 账户/分类/标签名字查表。 */
@HiltViewModel
class TxnListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeTxnList: ObserveTxnListUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    observeTags: ObserveTagsUseCase,
    observeLedgers: ObserveLedgersUseCase,
    private val deleteTxn: DeleteTxnUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val ledgerId: String =
        savedStateHandle.get<String>(LedgerRoutes.ARG_LEDGER_ID).orEmpty()

    private val notice = MutableStateFlow<TxnListNotice?>(null)

    private val content: StateFlow<TxnListContent> = combine(
        observeTxnList(TxnFilter(ledgerId = ledgerId)),
        observeAccounts(ledgerId, includeArchived = true),
        observeCategoryTree(ledgerId, includeArchived = true),
        observeTags(ledgerId),
        observeLedgers(),
    ) { txns, accounts, nodes, tags, ledgers ->
        TxnListContent(
            ledgerName = ledgers.firstOrNull { it.id == ledgerId }?.name.orEmpty(),
            groups = TxnDayGrouper.group(txns, timeProvider.zone),
            accountNames = accounts.accountNameMap(),
            categoryNames = nodes.categoryNameMap(),
            tagNames = tags.tagNameMap(),
            totalCount = txns.size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnListContent())

    val uiState: StateFlow<TxnListUiState> =
        combine(content, notice) { content, notice -> content.toUiState(notice) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnListUiState())

    fun onAction(action: TxnListAction) {
        when (action) {
            is TxnListAction.Delete -> viewModelScope.launch {
                val result = deleteTxn(action.txnId)
                notice.value = if (result.isSuccess) TxnListNotice.DELETED else TxnListNotice.DELETE_FAILED
            }

            TxnListAction.ConsumeNotice -> notice.update { null }
        }
    }

    /** 中间结构：只承载数据，一次性提示单独合并，避免每次删除都重建整棵列表流。 */
    private data class TxnListContent(
        val ledgerName: String = "",
        val groups: List<TxnDayGroup> = emptyList(),
        val accountNames: Map<String, String> = emptyMap(),
        val categoryNames: Map<String, String> = emptyMap(),
        val tagNames: Map<String, String> = emptyMap(),
        val totalCount: Int = 0,
        val loaded: Boolean = false,
    ) {
        fun toUiState(notice: TxnListNotice?) = TxnListUiState(
            isLoading = !loaded,
            ledgerName = ledgerName,
            groups = groups,
            accountNames = accountNames,
            categoryNames = categoryNames,
            tagNames = tagNames,
            totalCount = totalCount,
            notice = notice,
        )
    }
}