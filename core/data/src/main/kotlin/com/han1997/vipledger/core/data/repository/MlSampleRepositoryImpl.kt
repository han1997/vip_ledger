package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.MlTrainingSampleDao
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import com.han1997.vipledger.core.domain.repository.MlSampleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MlSampleRepositoryImpl @Inject constructor(
    private val mlTrainingSampleDao: MlTrainingSampleDao,
) : MlSampleRepository {

    override fun observeByLedger(ledgerId: String): Flow<List<MlTrainingSample>> =
        mlTrainingSampleDao.observeByLedger(ledgerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listByLedger(ledgerId: String): List<MlTrainingSample> =
        mlTrainingSampleDao.listByLedger(ledgerId).map { it.toDomain() }

    override suspend fun listUntrained(ledgerId: String): List<MlTrainingSample> =
        mlTrainingSampleDao.listUntrained(ledgerId).map { it.toDomain() }

    override suspend fun insertAll(samples: List<MlTrainingSample>): AppResult<Unit> =
        dataResult { mlTrainingSampleDao.insertAll(samples.map { it.toEntity() }) }

    override suspend fun markTrained(ids: Collection<String>, version: String): AppResult<Unit> =
        if (ids.isEmpty()) AppResult.success(Unit) else dataResult { mlTrainingSampleDao.markTrained(ids.toList(), version) }

    override suspend fun countByLedger(ledgerId: String): Int = mlTrainingSampleDao.countByLedger(ledgerId)
}