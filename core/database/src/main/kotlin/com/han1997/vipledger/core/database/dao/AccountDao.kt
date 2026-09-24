package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query(
        """
        SELECT * FROM account
        WHERE ledger_id = :ledgerId AND deleted_at IS NULL AND (:includeArchived OR is_archived = 0)
        ORDER BY sort_order ASC, created_at ASC
        """,
    )
    fun observeByLedger(ledgerId: String, includeArchived: Boolean): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT * FROM account
        WHERE ledger_id = :ledgerId AND deleted_at IS NULL AND (:includeArchived OR is_archived = 0)
        ORDER BY sort_order ASC, created_at ASC
        """,
    )
    suspend fun listByLedger(ledgerId: String, includeArchived: Boolean): List<AccountEntity>

    @Query("SELECT * FROM account WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE account SET current_balance_cents = :balanceCents, updated_at = :updatedAt WHERE id = :accountId")
    suspend fun updateBalance(accountId: String, balanceCents: Long, updatedAt: Long)

    @Query("UPDATE account SET is_archived = :archived, updated_at = :updatedAt WHERE id = :accountId")
    suspend fun setArchived(accountId: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE account SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :accountId")
    suspend fun softDelete(accountId: String, deletedAt: Long)
}