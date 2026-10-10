package com.han1997.vipledger.core.domain.usecase.home

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.BalanceCalculator
import com.han1997.vipledger.core.domain.calculator.StatsCalculator
import com.han1997.vipledger.core.domain.model.HomeOverview
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import com.han1997.vipledger.core.domain.calculator.TxnDayGrouper
import com.han1997.vipledger.core.domain.model.PeriodGranularity
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.time.DateRange
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * 首页概览：净资产 / 负债、本月与今日收支、最近账目。
 *
 * - 净资产与负债直接累加账户余额缓存（记账事务内维护），不重放全部流水；
 * - 本月收支走 [StatsCalculator] 的统一口径（只算已确认的收支）；
 * - 日期与时区观察流驱动查询重建，长驻跨日/月最多30秒刷新；七日范围可跨越月边界。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveHomeOverviewUseCase(
    private val ledgerRepository: LedgerRepository,
    private val accountRepository: AccountRepository,
    private val txnRepository: TxnRepository,
    private val timeProvider: TimeProvider,
) {

    operator fun invoke(ledgerId: String): Flow<HomeOverview> = ObserveLocalDayUseCase(timeProvider)().flatMapLatest { day ->
        val zone = day.zone
        val today = day.date
        val monthRange = DateRangePreset.THIS_MONTH.resolve(today)
        val weekRange = DateRange(today.minus(6, DateTimeUnit.DAY), today)
        val readRange = DateRange(minOf(monthRange.start, weekRange.start), monthRange.endInclusive)

        val ledgerFlow = ledgerRepository.observeAll(includeArchived = true)
        val accountFlow = accountRepository.observeByLedger(ledgerId, includeArchived = true)
        val monthFlow = txnRepository.observe(TxnFilter(ledgerId = ledgerId, range = readRange))
        val recentFlow = txnRepository.observe(TxnFilter(ledgerId = ledgerId, limit = RECENT_LIMIT, draftStatus = DraftStatus.CONFIRMED))

        combine(ledgerFlow, accountFlow, monthFlow, recentFlow) { ledgers, accounts, monthTxns, recentTxns ->
            val month = StatsCalculator.summarize(
                txns = monthTxns,
                categories = emptyList(),
                tags = emptyList(),
                range = monthRange,
                zone = zone,
            )
            HomeOverview(
                ledgerId = ledgerId,
                ledgerName = ledgers.firstOrNull { it.id == ledgerId }?.name.orEmpty(),
                netWorth = BalanceCalculator.netWorthFromCache(accounts),
                totalDebt = BalanceCalculator.totalDebtFromCache(accounts),
                monthIncome = month.income,
                monthExpense = month.expense,
                monthCount = month.count,
                todayExpense = todayExpense(monthTxns, today, zone),
                usableAccountCount = accounts.count { it.isUsable },
                recentTxns = recentTxns,
                totalAssets = BalanceCalculator.totalAssetsFromCache(accounts),
                todayIncome = TxnDayGrouper.countedSum(monthTxns.filter { it.occurredAt.toLocalDate(zone) == today }, TransactionType.INCOME),
                recentGroups = TxnDayGrouper.group(recentTxns, zone),
                lastSevenDays = StatsCalculator.byPeriod(monthTxns, PeriodGranularity.DAY, weekRange, zone),
            )
        }
    }

    private fun todayExpense(txns: List<Txn>, today: LocalDate, zone: TimeZone): Money =
        StatsCalculator.sumOfType(
            txns.filter { StatsCalculator.isCounted(it) && it.occurredAt.toLocalDate(zone) == today },
            TransactionType.EXPENSE,
        )

    companion object {
        const val RECENT_LIMIT: Int = 10
    }
}
