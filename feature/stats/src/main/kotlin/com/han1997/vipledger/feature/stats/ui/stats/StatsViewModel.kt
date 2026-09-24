package com.han1997.vipledger.feature.stats.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.designsystem.format.accountNameMap
import com.han1997.vipledger.core.designsystem.format.categoryNameMap
import com.han1997.vipledger.core.designsystem.format.tagNameMap
import com.han1997.vipledger.core.domain.model.PeriodGranularity
import com.han1997.vipledger.core.domain.model.PeriodStat
import com.han1997.vipledger.core.domain.model.StatsReport
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.usecase.account.ObserveAccountsUseCase
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveLedgersUseCase
import com.han1997.vipledger.core.domain.usecase.stats.BuildTxnCsvUseCase
import com.han1997.vipledger.core.domain.usecase.stats.ObserveStatsReportUseCase
import com.han1997.vipledger.core.domain.usecase.tag.ObserveTagsUseCase
import com.han1997.vipledger.core.domain.usecase.txn.ObserveTxnListUseCase
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.stats.navigation.StatsRoutes
import com.han1997.vipledger.feature.stats.ui.common.CsvExport
import com.han1997.vipledger.feature.stats.ui.common.csvSuggestedFileName
import com.han1997.vipledger.feature.stats.ui.common.imageSuggestedFileName
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
import kotlinx.datetime.LocalDate

/**
 * 统计页：时间段 + 粒度 + 维度，数字全部来自 [ObserveStatsReportUseCase]
 * （与首页「本月收支」同一套口径，见 `StatsMatchesHomeOverviewTest`）。
 *
 * 时间段默认落在「本月」，与首页对得上；「今天」在订阅建立时确定，与首页的处理一致。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeStatsReport: ObserveStatsReportUseCase,
    observeTxnList: ObserveTxnListUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    observeTags: ObserveTagsUseCase,
    observeLedgers: ObserveLedgersUseCase,
    private val buildCsv: BuildTxnCsvUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val ledgerId: String = savedStateHandle.get<String>(StatsRoutes.ARG_LEDGER_ID).orEmpty()

    private val today: LocalDate = timeProvider.nowMillis().toLocalDate(timeProvider.zone)

    private val selection = MutableStateFlow(StatsSelection())

    private val notice = MutableStateFlow<StatsNotice?>(null)

    private val csvExport = MutableStateFlow<CsvExport?>(null)

    private val imageExport = MutableStateFlow<String?>(null)

    private val report: StateFlow<StatsReport?> = selection
        .map { it.resolvedRange(today) to it.granularity }
        .distinctUntilChanged()
        .flatMapLatest { (range, granularity) -> observeStatsReport(ledgerId, range, granularity) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), null)

    /** 导出 CSV 用的是「区间内的账目清单」，与搜索/下钻共用同一份列格式。 */
    private val txns: StateFlow<List<Txn>> = selection
        .map { it.resolvedRange(today) }
        .distinctUntilChanged()
        .flatMapLatest { range ->
            observeTxnList(
                TxnFilter(
                    ledgerId = ledgerId,
                    range = range,
                    draftStatus = DraftStatus.CONFIRMED,
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    private val names = combine(
        observeAccounts(ledgerId, includeArchived = true),
        observeCategoryTree(ledgerId, includeArchived = true),
        observeTags(ledgerId),
    ) { accounts, nodes, tags ->
        StatsNames(
            categoryNames = nodes.categoryNameMap(),
            accountNames = accounts.accountNameMap(),
            tagNames = tags.tagNameMap(),
        )
    }

    private val content: StateFlow<StatsContent> = combine(
        report,
        selection,
        names,
        observeLedgers(),
    ) { report, selection, names, ledgers ->
        StatsContent(
            ledgerId = ledgerId,
            ledgerName = ledgers.firstOrNull { it.id == ledgerId }?.name.orEmpty(),
            today = today,
            selection = selection,
            range = report?.range,
            income = report?.summary?.income ?: Money.ZERO,
            expense = report?.summary?.expense ?: Money.ZERO,
            count = report?.summary?.count ?: 0,
            rows = report?.let {
                statsRowsOf(it, selection.dimension, selection.kind, names.accountNames)
            }.orEmpty(),
            periods = report?.periods.orEmpty(),
            categoryNames = names.categoryNames,
            accountNames = names.accountNames,
            tagNames = names.tagNames,
            loaded = report != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), StatsContent())

    val uiState: StateFlow<StatsUiState> =
        combine(content, notice, csvExport, imageExport) { content, notice, csv, image ->
            content.toUiState(notice, csv, image)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), StatsUiState())

    fun onAction(action: StatsAction) {
        when (action) {
            is StatsAction.SelectPreset -> selection.update { it.selectPreset(action.preset) }

            is StatsAction.SelectCustomRange -> selection.update { it.selectCustomRange(action.range) }

            is StatsAction.SelectGranularity -> selection.update { it.copy(granularity = action.granularity) }

            is StatsAction.SelectDimension -> selection.update { it.copy(dimension = action.dimension) }

            is StatsAction.SelectKind -> selection.update { it.copy(kind = action.kind) }

            StatsAction.ExportCsv -> csvExport.value = currentCsvExport()

            StatsAction.ExportImage -> imageExport.value =
                imageSuggestedFileName(IMAGE_PREFIX, today)

            is StatsAction.ExportFinished ->
                notice.value = if (action.success) StatsNotice.EXPORTED else StatsNotice.EXPORT_FAILED

            StatsAction.ConsumeExport -> csvExport.update { null }

            StatsAction.ConsumeImageExport -> imageExport.update { null }

            StatsAction.ConsumeNotice -> notice.update { null }
        }
    }

    private fun currentCsvExport(): CsvExport {
        val snapshot = content.value
        return CsvExport(
            fileName = csvSuggestedFileName(CSV_PREFIX, today),
            csv = buildCsv(
                txns = txns.value,
                categoryNames = snapshot.categoryNames,
                accountNames = snapshot.accountNames,
                tagNames = snapshot.tagNames,
            ),
        )
    }

    private data class StatsSelection(
        val preset: DateRangePreset? = DateRangePreset.THIS_MONTH,
        val customRange: DateRange? = null,
        val granularity: PeriodGranularity = PeriodGranularity.DAY,
        val dimension: StatsDimension = StatsDimension.CATEGORY,
        val kind: CategoryKind = CategoryKind.EXPENSE,
    ) {
        /** 统计页必须有一个具体区间：自定义 > 预设 > 兜底本月。 */
        fun resolvedRange(today: LocalDate): DateRange =
            customRange ?: preset?.resolve(today) ?: DateRangePreset.THIS_MONTH.resolve(today)

        fun selectPreset(value: DateRangePreset) = copy(preset = value, customRange = null)

        fun selectCustomRange(value: DateRange) = copy(preset = null, customRange = value)
    }

    private data class StatsNames(
        val categoryNames: Map<String, String>,
        val accountNames: Map<String, String>,
        val tagNames: Map<String, String>,
    )

    private data class StatsContent(
        val ledgerId: String = "",
        val ledgerName: String = "",
        val today: LocalDate = LocalDate(1970, 1, 1),
        val selection: StatsSelection = StatsSelection(),
        val range: DateRange? = null,
        val income: Money = Money.ZERO,
        val expense: Money = Money.ZERO,
        val count: Int = 0,
        val rows: List<StatsRow> = emptyList(),
        val periods: List<PeriodStat> = emptyList(),
        val categoryNames: Map<String, String> = emptyMap(),
        val accountNames: Map<String, String> = emptyMap(),
        val tagNames: Map<String, String> = emptyMap(),
        val loaded: Boolean = false,
    ) {
        fun toUiState(notice: StatsNotice?, csv: CsvExport?, image: String?) = StatsUiState(
            isLoading = !loaded,
            ledgerId = ledgerId,
            ledgerName = ledgerName,
            preset = selection.preset,
            customRange = selection.customRange,
            range = range,
            today = today,
            granularity = selection.granularity,
            dimension = selection.dimension,
            kind = selection.kind,
            income = income,
            expense = expense,
            count = count,
            rows = rows,
            periods = periods,
            notice = notice,
            pendingExport = csv,
            pendingImageExport = image,
        )
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        const val CSV_PREFIX = "stats"
        const val IMAGE_PREFIX = "stats"
    }
}