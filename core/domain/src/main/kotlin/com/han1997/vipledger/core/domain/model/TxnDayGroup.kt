package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/**
 * 明细列表 / 搜索结果的一天：当天的账目 + 当日合计。
 *
 * 当日合计只算参与收支统计的账目（转账、草稿、排除统计、已删都不计入），
 * 口径由 [com.han1997.vipledger.core.domain.calculator.TxnDayGrouper] 统一保证。
 */
data class TxnDayGroup(
    val date: LocalDate,
    val txns: List<Txn>,
    val income: Money,
    val expense: Money,
)