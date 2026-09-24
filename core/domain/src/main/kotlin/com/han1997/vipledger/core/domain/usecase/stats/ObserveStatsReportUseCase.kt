package com.han1997.vipledger.core.domain.usecase.stats

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.StatsCalculator
import com.han1997.vipledger.core.domain.model.PeriodGranularity
import com.han1997.vipledger.core.domain.model.StatsReport
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.TagRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.model.time.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 统计报表订阅：区间内的分类/标签/账户汇总 + 时间分桶。
 *
 * 口径全部交给 [StatsCalculator]，保证与首页「本月收支」一致（docs/DATA-MODEL.md 第 5 节）。
 */
class ObserveStatsReportUseCase(
    private val txnRepository: TxnRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val timeProvider: TimeProvider,
) {

    operator fun invoke(
        ledgerId: String,
        range: DateRange,
        granularity: PeriodGranularity = PeriodGranularity.DAY,
    ): Flow<StatsReport> {
        val zone = timeProvider.zone
        return combine(
            txnRepository.observe(TxnFilter(ledgerId = ledgerId, range = range)),
            categoryRepository.observeByLedger(ledgerId, includeArchived = true),
            tagRepository.observeByLedger(ledgerId),
        ) { txns, categories, tags ->
            StatsReport(
                ledgerId = ledgerId,
                range = range,
                granularity = granularity,
                summary = StatsCalculator.summarize(
                    txns = txns,
                    categories = categories,
                    tags = tags,
                    range = range,
                    zone = zone,
                ),
                periods = StatsCalculator.byPeriod(
                    txns = txns,
                    granularity = granularity,
                    range = range,
                    zone = zone,
                ),
            )
        }
    }
}