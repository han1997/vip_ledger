package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.BudgetDao
import com.han1997.vipledger.core.database.entity.*
import com.han1997.vipledger.core.domain.model.BudgetLimit
import com.han1997.vipledger.core.domain.repository.BudgetRepository
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class BudgetRepositoryImpl(private val dao: BudgetDao) : BudgetRepository {
    override fun observe(ledgerId: String) = dao.observe(ledgerId).map { it.map(::domain) }
    override suspend fun list(ledgerId: String) = dao.list(ledgerId).map(::domain)
    override suspend fun save(limits: List<BudgetLimit>) = dataResult { dao.save(limits.map { BudgetLimitEntity(it.id, it.ledgerId, it.month.toString(), it.scopeKey, it.amount?.cents, it.updatedAt) }) }
    override suspend fun claimAlert(ledgerId: String, month: LocalDate, scopeKey: String, threshold: Int, now: Long) = dataResult {
        dao.claim(BudgetAlertEntity(ledgerId, month.toString(), scopeKey, threshold, now)) != -1L
    }
    private fun domain(row: BudgetLimitEntity) = BudgetLimit(row.id, row.ledgerId, LocalDate.parse(row.month), row.scopeKey, row.amountCents?.let(Money::ofCents), row.updatedAt)
}
