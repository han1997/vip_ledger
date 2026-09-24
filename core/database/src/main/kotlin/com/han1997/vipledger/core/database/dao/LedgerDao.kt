package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.LedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query("SELECT * FROM ledger WHERE (:includeArchived OR archived_at IS NULL) ORDER BY sort_order ASC, created_at ASC")
    fun observeAll(includeArchived: Boolean): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledger WHERE (:includeArchived OR archived_at IS NULL) ORDER BY sort_order ASC, created_at ASC")
    suspend fun listAll(includeArchived: Boolean): List<LedgerEntity>

    @Query("SELECT * FROM ledger WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LedgerEntity?

    @Query("SELECT * FROM ledger WHERE is_default = 1 LIMIT 1")
    suspend fun getDefault(): LedgerEntity?

    @Query("SELECT COUNT(*) FROM ledger")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ledger: LedgerEntity)

    /** 默认账本全局唯一：一次性把其他账本降级。 */
    @Query("UPDATE ledger SET is_default = (CASE WHEN id = :id THEN 1 ELSE 0 END), updated_at = :updatedAt")
    suspend fun setDefault(id: String, updatedAt: Long)

    @Query("UPDATE ledger SET archived_at = :archivedAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archivedAt: Long?, updatedAt: Long)

    @Query("DELETE FROM ledger WHERE id = :id")
    suspend fun delete(id: String)
}