package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 交易附件（docs/DATA-MODEL.md 3.9）。文件本体在应用私有目录，库里只存相对路径。 */
@Entity(
    tableName = "attachment",
    foreignKeys = [
        ForeignKey(
            entity = TxnEntity::class,
            parentColumns = ["id"],
            childColumns = ["txn_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["txn_id"])],
)
data class AttachmentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "txn_id") val txnId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)