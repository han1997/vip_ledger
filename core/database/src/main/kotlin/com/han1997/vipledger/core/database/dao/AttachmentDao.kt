package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachment WHERE txn_id = :txnId AND deleted_at IS NULL ORDER BY created_at ASC")
    fun observeByTxn(txnId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachment WHERE txn_id = :txnId AND deleted_at IS NULL ORDER BY created_at ASC")
    suspend fun listByTxn(txnId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("UPDATE attachment SET deleted_at = :deletedAt WHERE id = :attachmentId")
    suspend fun softDelete(attachmentId: String, deletedAt: Long)
}