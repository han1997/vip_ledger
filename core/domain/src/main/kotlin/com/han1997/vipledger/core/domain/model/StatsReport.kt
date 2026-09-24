package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.time.DateRange

/**
 * 统计页一次性消费的数据：区间、时间粒度、汇总（分类/标签/账户）与时间分桶。
 *
 * 口径由 [StatsCalculator] 统一保证（docs/DATA-MODEL.md 第 5 节），首页与本页数字必须一致。
 */
data class StatsReport(
    val ledgerId: String,
    val range: DateRange,
    val granularity: PeriodGranularity,
    val summary: StatsSummary,
    val periods: List<PeriodStat> = emptyList(),
) {
    val isEmpty: Boolean get() = summary.count == 0
}