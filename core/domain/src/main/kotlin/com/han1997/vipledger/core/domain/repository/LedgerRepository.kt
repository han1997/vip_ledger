package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Ledger
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    fun observeAll(includeArchived: Boolean = false): Flow<List<Ledger>>

    suspend fun listAll(includeArchived: Boolean = false): List<Ledger>

    suspend fun getById(id: String): Ledger?

    suspend fun getDefault(): Ledger?

    suspend fun count(): Int

    suspend fun upsert(ledger: Ledger): AppResult<Unit>

    suspend fun setDefault(id: String, updatedAt: Long): AppResult<Unit>

    suspend fun setArchived(id: String, archivedAt: Long?): AppResult<Unit>

    /** 物理删除：仅允许在账本无任何账目时调用（由用例保证）。 */
    suspend fun delete(id: String): AppResult<Unit>
}