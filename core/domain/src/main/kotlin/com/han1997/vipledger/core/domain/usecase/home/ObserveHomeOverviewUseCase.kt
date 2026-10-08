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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * 首页概览：净资产 / 负债、本月与今日收支、最近账目。
 *
 * - 净资产与负债直接累加账户余额缓存（记账事务内维护），不重放全部流水；
 * - 本月收支走 [StatsCalculator] 的统一口径（只算已确认的收支）；
 * - 「今天」在订阅建立时确定。跨零点时页面的「今日支出」会在下次重组时刷新，
 *   属于可接受误差（长驻前台跨天不是主要场景）。
 */
class ObserveHomeOverviewUseCase(
    private val ledgerRepository: LedgerRepository,
    private val accountRepository: AccountRepository,
    private val txnRepository: TxnRepository,
    private val timeProvider: TimeProvider,
) {

    operator fun invoke(ledgerId: String): Flow<HomeOverview> {
        val zone = timeProvider.zone
        val now = timeProvider.nowMillis()
        val monthRange = DateRangePreset.THIS_MONTH.resolve(now.toLocalDate(zone))
        val today = now.toLocalDate(zone)

        val ledgerFlow = ledgerRepository.observeAll(includeArchived = true)
        val accountFlow = accountRepository.observeByLedger(ledgerId, includeArchived = true)
        val monthFlow = txnRepository.observe(TxnFilter(ledgerId = ledgerId, range = monthRange))
        val recentFlow = txnRepository.observe(TxnFilter(ledgerId = ledgerId, limit = RECENT_LIMIT))

        return combine(ledgerFlow, accountFlow, monthFlow, recentFlow) { ledgers, accounts, monthTxns, recentTxns ->
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