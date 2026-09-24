package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.AccountDao
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.model.money.Money
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
) : AccountRepository {

    override fun observeByLedger(ledgerId: String, includeArchived: Boolean): Flow<List<Account>> =
        accountDao.observeByLedger(ledgerId, includeArchived).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listByLedger(ledgerId: String, includeArchived: Boolean): List<Account> =
        accountDao.listByLedger(ledgerId, includeArchived).map { it.toDomain() }

    override suspend fun getById(id: String): Account? = accountDao.getById(id)?.toDomain()

    override suspend fun upsert(account: Account): AppResult<Unit> = dataResult { accountDao.upsert(account.toEntity()) }

    override suspend fun updateBalance(accountId: String, balance: Money, updatedAt: Long): AppResult<Unit> =
        dataResult { accountDao.updateBalance(accountId, balance.cents, updatedAt) }

    override suspend fun setArchived(accountId: String, archived: Boolean, updatedAt: Long): AppResult<Unit> =
        dataResult { accountDao.setArchived(accountId, archived, updatedAt) }

    override suspend fun softDelete(accountId: String, deletedAt: Long): AppResult<Unit> =
        dataResult { accountDao.softDelete(accountId, deletedAt) }
}