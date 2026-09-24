package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tag WHERE ledger_id = :ledgerId AND deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    fun observeByLedger(ledgerId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE ledger_id = :ledgerId AND deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    suspend fun listByLedger(ledgerId: String): List<TagEntity>

    /** 调用方必须保证 [ids] 非空（SQL 的 `IN ()` 是非法语法）。 */
    @Query("SELECT * FROM tag WHERE id IN (:ids) AND deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    suspend fun listByIds(ids: List<String>): List<TagEntity>

    @Query("SELECT * FROM tag WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: TagEntity)

    @Query("UPDATE tag SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :tagId")
    suspend fun softDelete(tagId: String, deletedAt: Long)
}