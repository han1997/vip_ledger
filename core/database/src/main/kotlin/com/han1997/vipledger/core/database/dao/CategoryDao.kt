package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query(
        """
        SELECT * FROM category
        WHERE ledger_id = :ledgerId AND deleted_at IS NULL AND (:includeArchived OR is_archived = 0)
        ORDER BY kind ASC, sort_order ASC, created_at ASC
        """,
    )
    fun observeByLedger(ledgerId: String, includeArchived: Boolean): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM category
        WHERE ledger_id = :ledgerId AND deleted_at IS NULL AND (:includeArchived OR is_archived = 0)
        ORDER BY kind ASC, sort_order ASC, created_at ASC
        """,
    )
    suspend fun listByLedger(ledgerId: String, includeArchived: Boolean): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM category WHERE parent_id = :parentId AND deleted_at IS NULL")
    suspend fun countChildren(parentId: String): Int

    @Query("UPDATE category SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :categoryId")
    suspend fun softDelete(categoryId: String, deletedAt: Long)
}