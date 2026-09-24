package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Attachment
import kotlinx.coroutines.flow.Flow

interface AttachmentRepository {
    fun observeByTxn(txnId: String): Flow<List<Attachment>>

    suspend fun listByTxn(txnId: String): List<Attachment>

    suspend fun insertAll(attachments: List<Attachment>): AppResult<Unit>

    suspend fun softDelete(attachmentId: String, deletedAt: Long): AppResult<Unit>
}