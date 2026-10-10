package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.LendingLink
import com.han1997.vipledger.core.domain.model.LendingRecord
import kotlinx.coroutines.flow.Flow

interface LendingRepository {
    fun observeRecords(ledgerId: String): Flow<List<LendingRecord>>

    fun observeLinks(ledgerId: String): Flow<List<LendingLink>>

    suspend fun record(id: String): LendingRecord?

    suspend fun links(lendingId: String): List<LendingLink>

    suspend fun linkForTxn(txnId: String): LendingLink?

    suspend fun save(record: LendingRecord): AppResult<Unit>

    suspend fun link(link: LendingLink): AppResult<Unit>
}
