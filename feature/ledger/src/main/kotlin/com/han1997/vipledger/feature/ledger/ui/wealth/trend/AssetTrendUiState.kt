package com.han1997.vipledger.feature.ledger.ui.wealth.trend

import com.han1997.vipledger.core.designsystem.format.suggestedFileName
import com.han1997.vipledger.core.domain.calculator.AssetHistoryCalculator
import com.han1997.vipledger.core.model.time.DateRange
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.util.Locale

/** 趋势页区间（独立于统计页的收支区间口径；「全部」从最早基线日开始）。 */
enum class TrendRange { LAST_30_DAYS, LAST_90_DAYS, THIS_YEAR, ALL }

/**
 * 趋势区间的日期范围。[earliestBaselineDay] 只在 [TrendRange.ALL] 使用：
 * 曲线从最早基线日开始（此前无锚点，不伪造历史）。
 */
fun trendDateRangeOf(range: TrendRange, today: LocalDate, earliestBaselineDay: LocalDate?): DateRange = when (range) {
    TrendRange.LAST_30_DAYS -> DateRange(today.minus(29, DateTimeUnit.DAY), today)
    TrendRange.LAST_90_DAYS -> DateRange(today.minus(89, DateTimeUnit.DAY), today)
    TrendRange.THIS_YEAR -> DateRange(LocalDate(today.year, 1, 1), LocalDate(today.year, 12, 31))
    TrendRange.ALL -> DateRange(earliestBaselineDay ?: today, today)
}

/** 导出文件名里的区间标识（ASCII）：`30d / 90d / y2026 / all`。 */
fun trendRangeLabel(range: TrendRange, today: LocalDate): String = when (range) {
    TrendRange.LAST_30_DAYS -> "30d"
    TrendRange.LAST_90_DAYS -> "90d"
    TrendRange.THIS_YEAR -> "y${today.year}"
    TrendRange.ALL -> "all"
}

/** 趋势页状态：只放数据与枚举，不放中文文案与 Compose 类型（ui.md）。 */
data class AssetTrendUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val ledgerId: String? = null,
    val ledgerName: String = "",
    val range: TrendRange = TrendRange.LAST_90_DAYS,
    /** 逐日资产口径点（空 = 无基线，页面显示空态）。 */
    val points: List<AssetHistoryCalculator.Point> = emptyList(),
    val today: LocalDate = LocalDate(1970, 1, 1),
) {
    val hasData: Boolean get() = points.isNotEmpty()

    /** 当前区间对应的日期范围（VM 计算曲线用；ALL 依赖最早基线）。 */
    fun dateRange(today: LocalDate, earliestBaselineDay: LocalDate?): DateRange =
        trendDateRangeOf(range, today, earliestBaselineDay)

    /** 选中日的口径点；越界/未选返回 null。 */
    fun pointAt(index: Int?): AssetHistoryCalculator.Point? =
        index?.takeIf { it in points.indices }?.let { points[it] }
}

sealed interface AssetTrendAction {
    data class SetRange(val value: TrendRange) : AssetTrendAction

    data object Retry : AssetTrendAction
}

/** 趋势 CSV：文件名带账本/区间/口径，首行注释带口径说明（P7「CSV 带账本/区间/口径」）。 */
data class TrendCsvExport(val fileName: String, val csv: String)

fun trendCsvOf(
    points: List<AssetHistoryCalculator.Point>,
    ledgerName: String,
    range: TrendRange,
    today: LocalDate,
): TrendCsvExport {
    val start = points.firstOrNull()?.date ?: today
    val end = points.lastOrNull()?.date ?: today
    val header = "# vipledger asset trend; ledger=$ledgerName; range=$start..$end; " +
        "basis=baseline+replay(confirm-only); unit=CNY yuan"
    val rows = listOf("date,assets,debt,net_worth") + points.map {
        "${it.date},${yuan(it.assetsCents)},${yuan(it.debtCents)},${yuan(it.netWorthCents)}"
    }
    return TrendCsvExport(
        fileName = suggestedFileName(
            prefix = "trend",
            ledgerName = ledgerName,
            rangeLabel = trendRangeLabel(range, today),
            date = today,
        ),
        csv = (listOf(header) + rows).joinToString("\n") + "\n",
    )
}

private fun yuan(cents: Long): String = String.format(Locale.US, "%.2f", cents / 100.0)