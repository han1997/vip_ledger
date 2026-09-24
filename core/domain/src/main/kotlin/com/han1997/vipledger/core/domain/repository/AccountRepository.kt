package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeByLedger(ledgerId: String, includeArchived: Boolean = false): Flow<List<Account>>

    suspend fun listByLedger(ledgerId: String, includeArchived: Boolean = false): List<Account>

    suspend fun getById(id: String): Account?

    suspend fun upsert(account: Account): AppResult<Unit>

    suspend fun updateBalance(accountId: String, balance: Money, updatedAt: Long): AppResult<Unit>

    suspend fun setArchived(accountId: String, archived: Boolean, updatedAt: Long): AppResult<Unit>

    suspend fun softDelete(accountId: String, deletedAt: Long): AppResult<Unit>
}