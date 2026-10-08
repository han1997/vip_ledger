package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDayGroup
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.datetime.TimeZone

/**
 * 按天分组账目并算出当日合计。
 *
 * 「明细」与「搜索结果」是同一份展示逻辑，抽在这里共用，避免两处各写一份而口径漂移
 * （与首页本月数字对齐的问题同理，见 .trellis/spec/guides/code-reuse-thinking-guide.md）。
 */
object TxnDayGrouper {

    /** 日期倒序（最新的在前）；[txns] 的顺序保持不变。 */
    fun group(txns: List<Txn>, zone: TimeZone): List<TxnDayGroup> =
        txns.groupBy { it.occurredAt.toLocalDate(zone) }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                TxnDayGroup(
                    date = date,
                    txns = list,
                    income = countedSum(list, TransactionType.INCOME),
                    expense = countedSum(list, TransactionType.EXPENSE),
                )
            }

    /** 只累加参与收支统计的账目（[StatsCalculator.isCounted]）。 */
    fun countedSum(txns: List<Txn>, type: TransactionType): Money =
        StatsCalculator.sumOfType(txns.filter(StatsCalculator::isCounted), type)
}