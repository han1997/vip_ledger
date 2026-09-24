package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** 账目 ↔ 标签关联（docs/DATA-MODEL.md 3.4）。流水删除时级联清理。 */
@Entity(
    tableName = "txn_tag",
    primaryKeys = ["txn_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TxnEntity::class,
            parentColumns = ["id"],
            childColumns = ["txn_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class TxnTagEntity(
    @ColumnInfo(name = "txn_id") val txnId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)