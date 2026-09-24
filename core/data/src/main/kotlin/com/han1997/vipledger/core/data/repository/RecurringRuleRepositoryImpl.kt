package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.RecurringRuleDao
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.domain.repository.RecurringRuleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecurringRuleRepositoryImpl @Inject constructor(
    private val recurringRuleDao: RecurringRuleDao,
) : RecurringRuleRepository {

    override fun observeByLedger(ledgerId: String): Flow<List<RecurringRule>> =
        recurringRuleDao.observeByLedger(ledgerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listByLedger(ledgerId: String): List<RecurringRule> =
        recurringRuleDao.listByLedger(ledgerId).map { it.toDomain() }

    override suspend fun listEnabled(ledgerId: String): List<RecurringRule> =
        recurringRuleDao.listEnabled(ledgerId).map { it.toDomain() }

    override suspend fun getById(id: String): RecurringRule? = recurringRuleDao.getById(id)?.toDomain()

    override suspend fun upsert(rule: RecurringRule): AppResult<Unit> =
        dataResult { recurringRuleDao.upsert(rule.toEntity()) }

    override suspend fun markGenerated(
        ruleId: String,
        generatedCount: Int,
        nextOccurrenceAt: Long?,
        lastGeneratedAt: Long,
        updatedAt: Long,
    ): AppResult<Unit> = dataResult {
        recurringRuleDao.markGenerated(ruleId, generatedCount, nextOccurrenceAt, lastGeneratedAt, updatedAt)
    }

    override suspend fun setEnabled(ruleId: String, enabled: Boolean, updatedAt: Long): AppResult<Unit> =
        dataResult { recurringRuleDao.setEnabled(ruleId, enabled, updatedAt) }

    override suspend fun softDelete(ruleId: String, deletedAt: Long): AppResult<Unit> =
        dataResult { recurringRuleDao.softDelete(ruleId, deletedAt) }
}