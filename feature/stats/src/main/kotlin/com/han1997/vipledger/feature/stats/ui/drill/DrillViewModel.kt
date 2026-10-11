package com.han1997.vipledger.feature.stats.ui.drill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.format.tagNameMap
import com.han1997.vipledger.core.domain.calculator.TxnDayGrouper
import com.han1997.vipledger.core.domain.model.SearchQuery
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDayGroup
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveLedgersUseCase
import com.han1997.vipledger.core.domain.usecase.search.SearchTxnsUseCase
import com.han1997.vipledger.core.domain.usecase.stats.BuildTxnCsvUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.stats.navigation.StatsRoutes
import com.han1997.vipledger.feature.stats.navigation.drillArgsOf
import com.han1997.vipledger.feature.stats.ui.common.CsvExport
import com.han1997.vipledger.core.designsystem.format.rangeLabelOf
import com.han1997.vipledger.core.designsystem.format.suggestedFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * 下钻明细：条件来自路由（固定不可改），只订阅、不提供筛选控件。
 *
 * 复用 [SearchTxnsUseCase]，所以「点一级分类下钻」会自动带出它的二级分类账目，
 * 与统计页的分类汇总口径一致。
 */
@HiltViewModel
class DrillViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchTxns: SearchTxnsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    observeTags: ObserveTagsUseCase,
    observeLedgers: ObserveLedgersUseCase,
    private val buildCsv: BuildTxnCsvUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val args = drillArgsOf(
        ledgerId = savedStateHandle.get<String>(StatsRoutes.ARG_LEDGER_ID),
        categoryId = savedStateHandle.get<String>(StatsRoutes.ARG_CATEGORY_ID),
        tagId = savedStateHandle.get<String>(StatsRoutes.ARG_TAG_ID),
        accountId = savedStateHandle.get<String>(StatsRoutes.ARG_ACCOUNT_ID),
        preset = savedStateHandle.get<String>(StatsRoutes.ARG_PRESET),
        from = savedStateHandle.get<String>(StatsRoutes.ARG_FROM),
        to = savedStateHandle.get<String>(StatsRoutes.ARG_TO),
    )

    /** 下钻只看已确认的收支，与统计页数字同口径。 */
    private val query = SearchQuery(
        ledgerId = args.ledgerId,
        preset = args.preset,
        customRange = args.range,
        categoryIds = args.categoryId?.let { setOf(it) } ?: emptySet(),
        tagIds = args.tagId?.let { setOf(it) } ?: emptySet(),
        accountIds = args.accountId?.let { setOf(it) } ?: emptySet(),
        draftStatus = DraftStatus.CONFIRMED,
    )

    private val notice = MutableStateFlow<DrillNotice?>(null)

    private val export = MutableStateFlow<CsvExport?>(null)

    private val txns: StateFlow<List<Txn>> =
        searchTxns(query).stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    private val content: StateFlow<DrillContent> = combine(
        txns,
        observeAccounts(args.ledgerId, includeArchived = true),
        observeCategoryTree(args.ledgerId, includeArchived = true),
        observeTags(args.ledgerId),
        observeLedgers(),
    ) { txns, accounts, nodes, tags, ledgers ->
        val categoryNames = nodes.categoryNameMap()
        val accountNames = accounts.accountNameMap()
        val tagNames = tags.tagNameMap()
        DrillContent(
            ledgerId = args.ledgerId,
            ledgerName = ledgers.firstOrNull { it.id == args.ledgerId }?.name.orEmpty(),
            dimensionLabel = args.categoryId?.let { categoryNames[it] }
                ?: args.tagId?.let { tagNames[it] }
                ?: args.accountId?.let { accountNames[it] },
            preset = args.preset,
            range = args.range,
            today = timeProvider.nowMillis().toLocalDate(timeProvider.zone),
            groups = TxnDayGrouper.group(txns, timeProvider.zone),
            totalCount = txns.size,
            income = TxnDayGrouper.countedSum(txns, TransactionType.INCOME),
            expense = TxnDayGrouper.countedSum(txns, TransactionType.EXPENSE),
            categoryNames = categoryNames,
            accountNames = accountNames,
            tagNames = tagNames,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), DrillContent())

    val uiState: StateFlow<DrillUiState> =
        combine(content, notice, export) { content, notice, export -> content.toUiState(notice, export) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), DrillUiState())

    fun onAction(action: DrillAction) {
        when (action) {
            DrillAction.ExportCsv -> export.value = currentCsvExport()

            is DrillAction.ExportFinished ->
                notice.value = if (action.success) DrillNotice.EXPORTED else DrillNotice.EXPORT_FAILED

            DrillAction.ConsumeExport -> export.update { null }

            DrillAction.ConsumeNotice -> notice.update { null }
        }
    }

    private fun currentCsvExport(): CsvExport {
        val snapshot = content.value
        val today = timeProvider.nowMillis().toLocalDate(timeProvider.zone)
        return CsvExport(
            fileName = suggestedFileName(
                prefix = CSV_PREFIX,
                ledgerName = snapshot.ledgerName,
                rangeLabel = rangeLabelOf(snapshot.preset, snapshot.range, today),
                date = today,
            ),
            csv = buildCsv(
                txns = snapshot.txns,
                categoryNames = snapshot.categoryNames,
                accountNames = snapshot.accountNames,
                tagNames = snapshot.tagNames,
            ),
        )
    }

    private data class DrillContent(
        val ledgerId: String = "",
        val ledgerName: String = "",
        val dimensionLabel: String? = null,
        val preset: DateRangePreset? = null,
        val range: DateRange? = null,
        val today: LocalDate = LocalDate(1970, 1, 1),
        val groups: List<TxnDayGroup> = emptyList(),
        val txns: List<Txn> = emptyList(),
        val totalCount: Int = 0,
        val income: Money = Money.ZERO,
        val expense: Money = Money.ZERO,
        val categoryNames: Map<String, String> = emptyMap(),
        val accountNames: Map<String, String> = emptyMap(),
        val tagNames: Map<String, String> = emptyMap(),
        val loaded: Boolean = false,
    ) {
        fun toUiState(notice: DrillNotice?, export: CsvExport?) = DrillUiState(
            isLoading = !loaded,
            ledgerId = ledgerId,
            ledgerName = ledgerName,
            dimensionLabel = dimensionLabel,
            preset = preset,
            range = range,
            today = today,
            groups = groups,
            totalCount = totalCount,
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
        const val CSV_PREFIX = "stats"
    }
}