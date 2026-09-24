package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.MlTrainingSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MlTrainingSampleDao {

    @Query("SELECT * FROM ml_training_sample WHERE ledger_id = :ledgerId ORDER BY created_at DESC")
    fun observeByLedger(ledgerId: String): Flow<List<MlTrainingSampleEntity>>

    @Query("SELECT * FROM ml_training_sample WHERE ledger_id = :ledgerId ORDER BY created_at DESC")
    suspend fun listByLedger(ledgerId: String): List<MlTrainingSampleEntity>

    @Query("SELECT * FROM ml_training_sample WHERE ledger_id = :ledgerId AND trained_version IS NULL ORDER BY created_at ASC")
    suspend fun listUntrained(ledgerId: String): List<MlTrainingSampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<MlTrainingSampleEntity>)

    /** 调用方必须保证 [ids] 非空。 */
    @Query("UPDATE ml_training_sample SET trained_version = :version WHERE id IN (:ids)")
    suspend fun markTrained(ids: List<String>, version: String)

    @Query("SELECT COUNT(*) FROM ml_training_sample WHERE ledger_id = :ledgerId")
    suspend fun countByLedger(ledgerId: String): Int
}