package com.han1997.vipledger.core.domain.usecase.budget

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.BudgetCalculator
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.repository.*
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.*

class ObserveBudgetUseCase(private val budgets: BudgetRepository, private val txns: TxnRepository, private val categories: CategoryRepository) {
    operator fun invoke(ledgerId: String, month: LocalDate, zone: TimeZone): Flow<BudgetReport> {
        val range = DateRangePreset.THIS_MONTH.resolve(month)
        return combine(budgets.observe(ledgerId), txns.observe(TxnFilter(ledgerId = ledgerId, range = range)), categories.observeByLedger(ledgerId, true)) { limits, entries, cats ->
            BudgetCalculator.report(ledgerId, range.start, limits, entries, cats, zone)
        }
    }
}

class SaveBudgetUseCase(
    private val budgets: BudgetRepository,
    private val ledgers: LedgerRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(ledgerId: String, month: LocalDate, scopeKey: String, amount: Money?): AppResult<Unit> {
        if (month.dayOfMonth != 1 || month.year !in 1900..9998 || (amount != null && amount.cents < 0))
            return AppResult.failure(AppError.Validation("Invalid budget"))
        val ledger = ledgers.getById(ledgerId)
        if (ledger == null || ledger.isArchived) return AppResult.failure(AppError.Validation("Unavailable ledger"))
        if (scopeKey.isNotEmpty() && amount != null) {
            val category = categories.getById(scopeKey)
            if (category == null || category.ledgerId != ledgerId || category.kind != CategoryKind.EXPENSE || category.deletedAt != null)
                return AppResult.failure(AppError.Validation("Unavailable expense category"))
        }
        return transactions.inTransaction {
            val rows = budgets.list(ledgerId)
            val now = time.nowMillis()
            val updates = mutableListOf(BudgetLimit(rows.find { it.month == month && it.scopeKey == scopeKey }?.id ?: ids.newId(), ledgerId, month, scopeKey, amount, now))
            val currentMonth = DateRangePreset.startOfMonth(now.toLocalDate(time.zone))
            if (month < currentMonth) {
                val next = month.plus(1, DateTimeUnit.MONTH)
                if (rows.none { it.month == next && it.scopeKey == scopeKey }) {
                    val old = BudgetCalculator.effective(rows, next).find { it.scopeKey == scopeKey }?.amount
                    updates += BudgetLimit(ids.newId(), ledgerId, next, scopeKey, old, now)
                }
            }
            budgets.save(updates)
        }
    }
}
