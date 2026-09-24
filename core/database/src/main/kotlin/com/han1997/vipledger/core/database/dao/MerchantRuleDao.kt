package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.MerchantRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rule WHERE ledger_id = :ledgerId ORDER BY priority ASC, created_at ASC")
    fun observeByLedger(ledgerId: String): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rule WHERE ledger_id = :ledgerId ORDER BY priority ASC, created_at ASC")
    suspend fun listByLedger(ledgerId: String): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rule WHERE ledger_id = :ledgerId AND enabled = 1 ORDER BY priority ASC, created_at ASC")
    suspend fun listEnabledByPriority(ledgerId: String): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("UPDATE merchant_rule SET hit_count = hit_count + 1, updated_at = :updatedAt WHERE id = :ruleId")
    suspend fun incrementHitCount(ruleId: String, updatedAt: Long)

    @Query("DELETE FROM merchant_rule WHERE id = :ruleId")
    suspend fun delete(ruleId: String)
}