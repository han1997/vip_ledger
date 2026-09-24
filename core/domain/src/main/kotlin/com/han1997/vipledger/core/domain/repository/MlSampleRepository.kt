package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import kotlinx.coroutines.flow.Flow

interface MlSampleRepository {
    fun observeByLedger(ledgerId: String): Flow<List<MlTrainingSample>>

    suspend fun listByLedger(ledgerId: String): List<MlTrainingSample>

    /** 尚未用于训练的样本（trained_version 为空）。 */
    suspend fun listUntrained(ledgerId: String): List<MlTrainingSample>

    suspend fun insertAll(samples: List<MlTrainingSample>): AppResult<Unit>

    suspend fun markTrained(ids: Collection<String>, version: String): AppResult<Unit>

    suspend fun countByLedger(ledgerId: String): Int
}