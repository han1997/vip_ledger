package com.han1997.vipledger.feature.stats.ui.drill

import com.han1997.vipledger.core.domain.model.TxnDayGroup
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.feature.stats.ui.common.CsvExport
import kotlinx.datetime.LocalDate

/**
 * 统计下钻页：把某个分组点（分类 / 标签 / 账户）在某个时间段里的账目摊开成明细。
 *
 * 口径与统计页严格一致（只算已确认的收支），否则「图上的笔数」和「点进去的条数」会对不上。
 */
data class DrillUiState(
    val isLoading: Boolean = true,
    val ledgerId: String = "",
    val ledgerName: String = "",
    /** 固定条件的展示名（分类 / 标签 / 账户名）；没有维度条件时为 null。 */
    val dimensionLabel: String? = null,
    val preset: DateRangePreset? = null,
    val range: DateRange? = null,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val groups: List<TxnDayGroup> = emptyList(),
    val totalCount: Int = 0,
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val categoryNames: Map<String, String> = emptyMap(),
    val accountNames: Map<String, String> = emptyMap(),
    val tagNames: Map<String, String> = emptyMap(),
    val notice: DrillNotice? = null,
    val pendingExport: CsvExport? = null,
)

enum class DrillNotice { EXPORTED, EXPORT_FAILED }

sealed interface DrillAction {
    data object ExportCsv : DrillAction

    data class ExportFinished(val success: Boolean) : DrillAction

    data object ConsumeExport : DrillAction

    data object ConsumeNotice : DrillAction
}