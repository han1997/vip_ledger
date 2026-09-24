package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.LedgerDao
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LedgerRepositoryImpl @Inject constructor(
    private val ledgerDao: LedgerDao,
    private val timeProvider: TimeProvider,
) : LedgerRepository {

    override fun observeAll(includeArchived: Boolean): Flow<List<Ledger>> =
        ledgerDao.observeAll(includeArchived).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listAll(includeArchived: Boolean): List<Ledger> =
        ledgerDao.listAll(includeArchived).map { it.toDomain() }

    override suspend fun getById(id: String): Ledger? = ledgerDao.getById(id)?.toDomain()

    override suspend fun getDefault(): Ledger? = ledgerDao.getDefault()?.toDomain()

    override suspend fun count(): Int = ledgerDao.count()

    override suspend fun upsert(ledger: Ledger): AppResult<Unit> = dataResult { ledgerDao.upsert(ledger.toEntity()) }

    override suspend fun setDefault(id: String, updatedAt: Long): AppResult<Unit> =
        dataResult { ledgerDao.setDefault(id, updatedAt) }

    override suspend fun setArchived(id: String, archivedAt: Long?): AppResult<Unit> =
        dataResult { ledgerDao.setArchived(id, archivedAt, timeProvider.nowMillis()) }

    override suspend fun delete(id: String): AppResult<Unit> = dataResult { ledgerDao.delete(id) }
}