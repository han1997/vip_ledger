package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.AttachmentDao
import com.han1997.vipledger.core.domain.model.Attachment
import com.han1997.vipledger.core.domain.repository.AttachmentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AttachmentRepositoryImpl @Inject constructor(
    private val attachmentDao: AttachmentDao,
) : AttachmentRepository {

    override fun observeByTxn(txnId: String): Flow<List<Attachment>> =
        attachmentDao.observeByTxn(txnId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listByTxn(txnId: String): List<Attachment> =
        attachmentDao.listByTxn(txnId).map { it.toDomain() }

    override suspend fun insertAll(attachments: List<Attachment>): AppResult<Unit> =
        dataResult { attachmentDao.insertAll(attachments.map { it.toEntity() }) }

    override suspend fun softDelete(attachmentId: String, deletedAt: Long): AppResult<Unit> =
        dataResult { attachmentDao.softDelete(attachmentId, deletedAt) }
}