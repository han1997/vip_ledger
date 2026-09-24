package com.han1997.vipledger.feature.stats.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.format.tagNameMap
import com.han1997.vipledger.core.domain.calculator.TxnDayGrouper
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDayGroup
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveLedgersUseCase
import com.han1997.vipledger.core.domain.usecase.search.SearchTxnsUseCase
import com.han1997.vipledger.core.domain.usecase.stats.BuildTxnCsvUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DeleteTxnUseCase
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.stats.navigation.StatsRoutes
import com.han1997.vipledger.feature.stats.ui.common.CsvExport
import com.han1997.vipledger.feature.stats.ui.common.FilterOption
import com.han1997.vipledger.feature.stats.ui.common.TxnFilterState
import com.han1997.vipledger.feature.stats.ui.common.csvSuggestedFileName
import com.han1997.vipledger.feature.stats.ui.common.toAccountOptions
import com.han1997.vipledger.feature.stats.ui.common.toCategoryOptions
import com.han1997.vipledger.feature.stats.ui.common.toTagOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 组合搜索：条件一变就重新订阅结果（`flatMapLatest`），不保留历史结果。
 *
 * 条件区的候选项（分类/账户/标签）与结果分开订阅，勾选条件不会重建候选项；
 * 导出时把当前结果整体交给界面层，由界面走 SAF 写文件（仓库层不碰 Android API）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchTxns: SearchTxnsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    observeTags: ObserveTagsUseCase,
    observeLedgers: ObserveLedgersUseCase,
    private val deleteTxn: DeleteTxnUseCase,
    private val buildCsv: BuildTxnCsvUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val ledgerId: String = savedStateHandle.get<String>(StatsRoutes.ARG_LEDGER_ID).orEmpty()

    private val filter = MutableStateFlow(TxnFilterState())

    private val notice = MutableStateFlow<SearchNotice?>(null)

    private val export = MutableStateFlow<CsvExport?>(null)

    private val options = combine(
        observeAccounts(ledgerId, includeArchived = true),
        observeCategoryTree(ledgerId, includeArchived = true),
        observeTags(ledgerId),
    ) { accounts, nodes, tags ->
        SearchOptions(
            categoryOptions = nodes.toCategoryOptions(),
            accountOptions = accounts.toAccountOptions(),
            tagOptions = tags.toTagOptions(),
            categoryNames = nodes.categoryNameMap(),
            accountNames = accounts.accountNameMap(),
            tagNames = tags.tagNameMap(),
        )
    }

    private val results: StateFlow<List<Txn>> = filter
        .map { it.toQuery(ledgerId) }
        .distinctUntilChanged()
        .flatMapLatest { query -> searchTxns(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    private val content: StateFlow<SearchContent> = combine(
        results,
        filter,
        options,
        observeLedgers(),
    ) { txns, filter, options, ledgers ->
        SearchContent(
            ledgerId = ledgerId,
            ledgerName = ledgers.firstOrNull { it.id == ledgerId }?.name.orEmpty(),
            filter = filter,
            categoryOptions = options.categoryOptions,
            accountOptions = options.accountOptions,
            tagOptions = options.tagOptions,
            categoryNames = options.categoryNames,
            accountNames = options.accountNames,
            tagNames = options.tagNames,
            txns = txns,
            groups = TxnDayGrouper.group(txns, timeProvider.zone),
            income = TxnDayGrouper.countedSum(txns, TransactionType.INCOME),
            expense = TxnDayGrouper.countedSum(txns, TransactionType.EXPENSE),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), SearchContent())

    val uiState: StateFlow<SearchUiState> = combine(content, notice, export) { content, notice, export ->
        content.toUiState(notice, export)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), SearchUiState())

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.KeywordChanged -> filter.update { it.copy(keyword = action.value) }

            is SearchAction.SelectPreset -> filter.update { it.selectPreset(action.preset) }

            is SearchAction.SelectCustomRange -> filter.update { it.selectCustomRange(action.range) }

            is SearchAction.ToggleCategory -> filter.update { it.toggleCategory(action.id) }

            is SearchAction.ToggleTag -> filter.update { it.toggleTag(action.id) }

            is SearchAction.ToggleAccount -> filter.update { it.toggleAccount(action.id) }

            is SearchAction.ToggleType -> filter.update { it.toggleType(action.type) }

            is SearchAction.MinAmountChanged -> filter.update { it.copy(minAmountText = action.value) }

            is SearchAction.MaxAmountChanged -> filter.update { it.copy(maxAmountText = action.value) }

            is SearchAction.IncludeDraftsChanged -> filter.update { it.copy(includeDrafts = action.value) }

            SearchAction.ClearConditions -> filter.value = TxnFilterState()

            is SearchAction.Delete -> viewModelScope.launch {
                val result = deleteTxn(action.txnId)
                notice.value = if (result.isSuccess) SearchNotice.DELETED else SearchNotice.DELETE_FAILED
            }

            SearchAction.ExportCsv -> export.value = currentCsvExport()

            is SearchAction.ExportFinished ->
                notice.value = if (action.success) SearchNotice.EXPORTED else SearchNotice.EXPORT_FAILED

            SearchAction.ConsumeExport -> export.update { null }

            SearchAction.ConsumeNotice -> notice.update { null }
        }
    }

    private fun currentCsvExport(): CsvExport {
        val snapshot = content.value
        return CsvExport(
            fileName = csvSuggestedFileName(
                prefix = CSV_PREFIX,
                date = timeProvider.nowMillis().toLocalDate(timeProvider.zone),
            ),
            csv = buildCsv(
                txns = snapshot.txns,
                categoryNames = snapshot.categoryNames,
                accountNames = snapshot.accountNames,
                tagNames = snapshot.tagNames,
            ),
        )
    }

    /** 候选列表只承载数据，与结果分开合并，勾选条件时不会重建它们。 */
    private data class SearchOptions(
        val categoryOptions: List<FilterOption>,
        val accountOptions: List<FilterOption>,
        val tagOptions: List<FilterOption>,
        val categoryNames: Map<String, String>,
        val accountNames: Map<String, String>,
        val tagNames: Map<String, String>,
    )

    /** 中间结构：只承载数据，一次性提示与导出请求单独合并。 */
    private data class SearchContent(
        val ledgerId: String = "",
        val ledgerName: String = "",
        val filter: TxnFilterState = TxnFilterState(),
        val categoryOptions: List<FilterOption> = emptyList(),
        val accountOptions: List<FilterOption> = emptyList(),
        val tagOptions: List<FilterOption> = emptyList(),
        val categoryNames: Map<String, String> = emptyMap(),
        val accountNames: Map<String, String> = emptyMap(),
        val tagNames: Map<String, String> = emptyMap(),
        val txns: List<Txn> = emptyList(),
        val groups: List<TxnDayGroup> = emptyList(),
        val income: Money = Money.ZERO,
        val expense: Money = Money.ZERO,
        val loaded: Boolean = false,
    ) {
        fun toUiState(notice: SearchNotice?, export: CsvExport?) = SearchUiState(
            isLoading = !loaded,
            ledgerId = ledgerId,
            ledgerName = ledgerName,
            filter = filter,
            categoryOptions = categoryOptions,
            accountOptions = accountOptions,
            tagOptions = tagOptions,
            groups = groups,
            totalCount = txns.size,
            income = income,
            expense = expense,
            categoryNames = categoryNames,
            accountNames = accountNames,
            tagNames = tagNames,
            notice = notice,
            pendingExport = export,
        )
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        const val CSV_PREFIX = "search"
    }
}