package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnFilter
import kotlinx.coroutines.flow.Flow

interface TxnRepository {
    fun observe(filter: TxnFilter): Flow<List<Txn>>

    suspend fun list(filter: TxnFilter): List<Txn>

    suspend fun getById(id: String): Txn?

    /** 命中 account_id 或 to_account_id 的全部流水，用于重算账户余额。 */
    suspend fun listByAccount(accountId: String, includeDeleted: Boolean = false): List<Txn>

    suspend fun listByLedger(ledgerId: String, includeDeleted: Boolean = false): List<Txn>

    suspend fun insert(txn: Txn): AppResult<Unit>

    suspend fun update(txn: Txn): AppResult<Unit>

    suspend fun softDelete(id: String, deletedAt: Long): AppResult<Unit>

    suspend fun restore(id: String, updatedAt: Long): AppResult<Unit>

    suspend fun existsImportHash(hash: String): Boolean

    suspend fun countByLedger(ledgerId: String): Int

    /** 该分类下（不含子分类）未删除的账目数，用于「被引用则不许删分类」。 */
    suspend fun countByCategory(categoryId: String): Int
}