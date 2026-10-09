package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

object BudgetCalculator {
    fun effective(limits: List<BudgetLimit>, month: LocalDate): List<BudgetLimit> = limits
        .filter { it.month <= month }.groupBy { it.scopeKey }
        .values.map { rows -> rows.maxBy { it.month } }.sortedBy { it.scopeKey }

    fun report(ledgerId: String, month: LocalDate, limits: List<BudgetLimit>, txns: List<Txn>, categories: List<Category>, zone: TimeZone): BudgetReport {
        val range = DateRangePreset.THIS_MONTH.resolve(month)
        val counted = StatsCalculator.countedIn(txns.filter { it.ledgerId == ledgerId }, range, zone)
        val parents = categories.filter { it.ledgerId == ledgerId }.associate { it.id to it.parentId }
        return BudgetReport(ledgerId, range.start, effective(limits.filter { it.ledgerId == ledgerId }, range.start)
            .filter { it.amount != null }.map { limit ->
                val scoped = counted.filter { limit.scopeKey.isEmpty() || it.categoryId == limit.scopeKey || parents[it.categoryId] == limit.scopeKey }
                BudgetProgress(limit, StatsCalculator.sumOfType(scoped, TransactionType.EXPENSE))
            })
    }
}
