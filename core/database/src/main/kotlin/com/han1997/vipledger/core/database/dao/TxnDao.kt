package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.database.entity.TxnTagEntity
import com.han1997.vipledger.core.database.entity.TxnWithTagIds
import kotlinx.coroutines.flow.Flow

/**
 * 账目流水 DAO。
 *
 * 列表/搜索用一条静态 SQL 覆盖全部可选条件（docs/DATA-MODEL.md 3.5 的搜索实现约定）：
 * - 可空标量用 `:x IS NULL OR ...` 跳过；
 * - 集合条件传 `,id1,id2,` 形式的 CSV，用 `INSTR` 判断，避免 `IN ()` 空集合语法错误；
 * - `LIMIT :limit` 传 -1 表示不限制。
 */
@Dao
interface TxnDao {

    @Transaction
    @Query(TXN_FILTER_SQL)
    fun observeFiltered(
        ledgerId: String,
        includeDeleted: Boolean,
        from: Long?,
        to: Long?,
        keyword: String?,
        typeCsv: String?,
        categoryCsv: String?,
        accountCsv: String?,
        tagCsv: String?,
        minAmount: Long?,
        maxAmount: Long?,
        draftStatus: String?,
        limit: Int,
    ): Flow<List<TxnWithTagIds>>

    @Transaction
    @Query(TXN_FILTER_SQL)
    suspend fun listFiltered(
        ledgerId: String,
        includeDeleted: Boolean,
        from: Long?,
        to: Long?,
        keyword: String?,
        typeCsv: String?,
        categoryCsv: String?,
        accountCsv: String?,
        tagCsv: String?,
        minAmount: Long?,
        maxAmount: Long?,
        draftStatus: String?,
        limit: Int,
    ): List<TxnWithTagIds>

    @Transaction
    @Query("SELECT * FROM txn WHERE id = :id LIMIT 1")
    suspend fun getWithTags(id: String): TxnWithTagIds?

    @Query("SELECT * FROM txn WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TxnEntity?

    @Query(
        """
        SELECT * FROM txn
        WHERE (account_id = :accountId OR to_account_id = :accountId)
          AND (:includeDeleted OR deleted_at IS NULL)
        ORDER BY occurred_at ASC, created_at ASC, id ASC
        """,
    )
    suspend fun listByAccount(accountId: String, includeDeleted: Boolean): List<TxnEntity>

    @Query("SELECT * FROM txn WHERE ledger_id = :ledgerId AND (:includeDeleted OR deleted_at IS NULL) ORDER BY occurred_at DESC, created_at DESC, id DESC")
    suspend fun listByLedger(ledgerId: String, includeDeleted: Boolean): List<TxnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(txn: TxnEntity)

    @Update
    suspend fun update(txn: TxnEntity): Int

    @Query("DELETE FROM txn_tag WHERE txn_id = :txnId")
    suspend fun clearTagLinks(txnId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagLinks(links: List<TxnTagEntity>)

    /** 仓库将SQL异常转为Failure之前，确保流水和标签已整体回滚。 */
    @Transaction
    suspend fun writeWithTags(txn: TxnEntity, links: List<TxnTagEntity>, updating: Boolean) {
        if (updating) check(update(txn) == 1) { "Entry no longer exists" } else upsert(txn)
        clearTagLinks(txn.id)
        if (links.isNotEmpty()) insertTagLinks(links)
    }

    @Query("UPDATE txn SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE txn SET deleted_at = NULL, updated_at = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long)

    @Query("UPDATE txn SET draft_status = :draftStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateDraftStatus(id: String, draftStatus: String, updatedAt: Long)

    @Query("DELETE FROM txn WHERE id = :id")
    suspend fun deleteHard(id: String)

    @Query("SELECT COUNT(*) FROM txn WHERE ledger_id = :ledgerId AND deleted_at IS NULL")
    suspend fun countByLedger(ledgerId: String): Int

    /** 删除分类前的引用检查（软删的账目不算引用）。 */
    @Query("SELECT COUNT(*) FROM txn WHERE category_id = :categoryId AND deleted_at IS NULL")
    suspend fun countByCategory(categoryId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM txn WHERE import_hash = :hash)")
    suspend fun existsImportHash(hash: String): Boolean

    companion object {
        const val TXN_FILTER_SQL: String = """
            SELECT * FROM txn
            WHERE ledger_id = :ledgerId
              AND (:includeDeleted OR deleted_at IS NULL)
              AND (:from IS NULL OR occurred_at >= :from)
              AND (:to IS NULL OR occurred_at < :to)
              AND (:keyword IS NULL OR title LIKE '%' || :keyword || '%' OR remark LIKE '%' || :keyword || '%')
              AND (:typeCsv IS NULL OR INSTR(:typeCsv, ',' || type || ',') > 0)
              AND (:categoryCsv IS NULL OR INSTR(:categoryCsv, ',' || COALESCE(category_id, '') || ',') > 0)
              AND (:accountCsv IS NULL OR INSTR(:accountCsv, ',' || account_id || ',') > 0
                   OR INSTR(:accountCsv, ',' || COALESCE(to_account_id, '') || ',') > 0)
              AND (:tagCsv IS NULL OR EXISTS (
                    SELECT 1 FROM txn_tag tt WHERE tt.txn_id = txn.id AND INSTR(:tagCsv, ',' || tt.tag_id || ',') > 0))
              AND (:minAmount IS NULL OR ABS(amount_cents) >= :minAmount)
              AND (:maxAmount IS NULL OR ABS(amount_cents) <= :maxAmount)
              AND (:draftStatus IS NULL OR draft_status = :draftStatus)
            ORDER BY occurred_at DESC, created_at DESC, id DESC
            LIMIT :limit
        """
    }
}
