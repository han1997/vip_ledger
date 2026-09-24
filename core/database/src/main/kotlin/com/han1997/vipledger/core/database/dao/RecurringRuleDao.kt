package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rule WHERE ledger_id = :ledgerId AND deleted_at IS NULL ORDER BY created_at ASC")
    fun observeByLedger(ledgerId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rule WHERE ledger_id = :ledgerId AND deleted_at IS NULL ORDER BY created_at ASC")
    suspend fun listByLedger(ledgerId: String): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rule WHERE ledger_id = :ledgerId AND enabled = 1 AND deleted_at IS NULL ORDER BY created_at ASC")
    suspend fun listEnabled(ledgerId: String): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rule WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RecurringRuleEntity)

    @Query(
        """
        UPDATE recurring_rule
        SET generated_count = :generatedCount, next_occurrence_at = :nextOccurrenceAt,
            last_generated_at = :lastGeneratedAt, updated_at = :updatedAt
        WHERE id = :ruleId
        """,
    )
    suspend fun markGenerated(
        ruleId: String,
        generatedCount: Int,
        nextOccurrenceAt: Long?,
        lastGeneratedAt: Long,
        updatedAt: Long,
    )

    @Query("UPDATE recurring_rule SET enabled = :enabled, updated_at = :updatedAt WHERE id = :ruleId")
    suspend fun setEnabled(ruleId: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE recurring_rule SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :ruleId")
    suspend fun softDelete(ruleId: String, deletedAt: Long)
}