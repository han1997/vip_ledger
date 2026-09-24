package com.han1997.vipledger.feature.stats.ui.search

import com.han1997.vipledger.core.domain.model.TxnDayGroup
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.feature.stats.ui.common.CsvExport
import com.han1997.vipledger.feature.stats.ui.common.FilterOption
import com.han1997.vipledger.feature.stats.ui.common.TxnFilterState

/**
 * 搜索页状态：左边的条件 + 右边的结果。
 *
 * 结果按天分组（与「明细」列表共用 `TxnDayGrouper` 的口径），合计只算参与收支统计的账目。
 */
data class SearchUiState(
    val isLoading: Boolean = true,
    val ledgerId: String = "",
    val ledgerName: String = "",
    val filter: TxnFilterState = TxnFilterState(),
    val categoryOptions: List<FilterOption> = emptyList(),
    val accountOptions: List<FilterOption> = emptyList(),
    val tagOptions: List<FilterOption> = emptyList(),
    val groups: List<TxnDayGroup> = emptyList(),
    val totalCount: Int = 0,
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val categoryNames: Map<String, String> = emptyMap(),
    val accountNames: Map<String, String> = emptyMap(),
    val tagNames: Map<String, String> = emptyMap(),
    val notice: SearchNotice? = null,
    /** 一次性导出请求：界面拿到后弹 SAF 选择器，写完文件再回传结果。 */
    val pendingExport: CsvExport? = null,
)

/** 一次性提示；文案在界面层查 `strings.xml`（状态里不放中文）。 */
enum class SearchNotice { DELETED, DELETE_FAILED, EXPORTED, EXPORT_FAILED }

sealed interface SearchAction {
    data class KeywordChanged(val value: String) : SearchAction

    data class SelectPreset(val preset: DateRangePreset?) : SearchAction

    data class SelectCustomRange(val range: DateRange) : SearchAction

    data class ToggleCategory(val id: String) : SearchAction

    data class ToggleTag(val id: String) : SearchAction

    data class ToggleAccount(val id: String) : SearchAction

    data class ToggleType(val type: TransactionType) : SearchAction

    data class MinAmountChanged(val value: String) : SearchAction

    data class MaxAmountChanged(val value: String) : SearchAction

    data class IncludeDraftsChanged(val value: Boolean) : SearchAction

    data object ClearConditions : SearchAction

    data class Delete(val txnId: String) : SearchAction

    data object ExportCsv : SearchAction

    data class ExportFinished(val success: Boolean) : SearchAction

    data object ConsumeExport : SearchAction

    data object ConsumeNotice : SearchAction
}