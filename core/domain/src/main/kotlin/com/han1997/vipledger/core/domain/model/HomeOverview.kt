package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.money.Money

/**
 * 首页概览（M2）。口径见 docs/DATA-MODEL.md 第 5 节：
 * 只有已确认、未删除、未排除的收支参与月度数字；净资产直接取账户余额缓存。
 */
data class HomeOverview(
    val ledgerId: String,
    val ledgerName: String,
    val netWorth: Money = Money.ZERO,
    val totalDebt: Money = Money.ZERO,
    val monthIncome: Money = Money.ZERO,
    val monthExpense: Money = Money.ZERO,
    val monthCount: Int = 0,
    val todayExpense: Money = Money.ZERO,
    val usableAccountCount: Int = 0,
    val recentTxns: List<Txn> = emptyList(),
    val totalAssets: Money = Money.ZERO,
    val todayIncome: Money = Money.ZERO,
    val recentGroups: List<TxnDayGroup> = emptyList(),
    val lastSevenDays: List<PeriodStat> = emptyList(),
) {
    val monthBalance: Money get() = monthIncome - monthExpense
}
