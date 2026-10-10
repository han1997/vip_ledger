package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 报销项目（docs/DATA-MODEL.md 3.8）。待报销不进净资产，故不建应收账户。 */
@Entity(
    tableName = "reimbursement",
    foreignKeys = [
        ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledger_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("ledger_id")],
)
data class ReimbursementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val title: String,
    val counterparty: String,
    val remark: String,
    val closed: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 报销项目关联的原支出；一个支出最多属于一个项目。 */
@Entity(
    tableName = "reimbursement_item",
    foreignKeys = [
        ForeignKey(entity = ReimbursementEntity::class, parentColumns = ["id"], childColumns = ["reimbursement_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TxnEntity::class, parentColumns = ["id"], childColumns = ["txn_id"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("reimbursement_id")],
)
data class ReimbursementItemEntity(
    @PrimaryKey @ColumnInfo(name = "txn_id") val txnId: String,
    @ColumnInfo(name = "reimbursement_id") val reimbursementId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** 报销到账：指向一条 REFUND 流水，该流水再关联原支出。 */
@Entity(
    tableName = "reimbursement_receipt",
    foreignKeys = [
        ForeignKey(entity = ReimbursementEntity::class, parentColumns = ["id"], childColumns = ["reimbursement_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TxnEntity::class, parentColumns = ["id"], childColumns = ["txn_id"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("reimbursement_id")],
)
data class ReimbursementReceiptEntity(
    @PrimaryKey @ColumnInfo(name = "txn_id") val txnId: String,
    @ColumnInfo(name = "reimbursement_id") val reimbursementId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
