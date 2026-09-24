package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeByLedger(ledgerId: String): Flow<List<Tag>>

    suspend fun listByLedger(ledgerId: String): List<Tag>

    suspend fun listByIds(ids: Collection<String>): List<Tag>

    suspend fun getById(id: String): Tag?

    suspend fun upsert(tag: Tag): AppResult<Unit>

    suspend fun softDelete(tagId: String, deletedAt: Long): AppResult<Unit>
}