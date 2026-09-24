package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.RecurringRule
import kotlinx.coroutines.flow.Flow

interface RecurringRuleRepository {
    fun observeByLedger(ledgerId: String): Flow<List<RecurringRule>>

    suspend fun listByLedger(ledgerId: String): List<RecurringRule>

    suspend fun listEnabled(ledgerId: String): List<RecurringRule>

    suspend fun getById(id: String): RecurringRule?

    suspend fun upsert(rule: RecurringRule): AppResult<Unit>

    suspend fun markGenerated(
        ruleId: String,
        generatedCount: Int,
        nextOccurrenceAt: Long?,
        lastGeneratedAt: Long,
        updatedAt: Long,
    ): AppResult<Unit>

    suspend fun setEnabled(ruleId: String, enabled: Boolean, updatedAt: Long): AppResult<Unit>

    suspend fun softDelete(ruleId: String, deletedAt: Long): AppResult<Unit>
}