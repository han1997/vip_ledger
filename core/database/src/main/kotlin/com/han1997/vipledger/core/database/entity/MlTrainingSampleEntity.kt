package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.MlSampleSource

/** 端侧分类器训练样本（docs/DATA-MODEL.md 3.10）。 */
@Entity(
    tableName = "ml_training_sample",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "created_at"]),
        Index(value = ["trained_version"]),
        Index(value = ["category_id"]),
    ],
)
data class MlTrainingSampleEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "source") val source: MlSampleSource,
    @ColumnInfo(name = "trained_version") val trainedVersion: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)