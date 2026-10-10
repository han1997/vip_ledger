package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.InvestmentLinkKind

/** 理财台账（P4）：只记金额，不接行情、不自动估值。 */
@Entity(
    tableName = "investment",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_id"]),
        Index(value = ["account_id"]),
    ],
)
data class InvestmentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "remark") val remark: String = "",
    @ColumnInfo(name = "closed") val closed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 理财操作流水与台账的关联。 */
@Entity(
    tableName = "investment_link",
    foreignKeys = [
        ForeignKey(
            entity = TxnEntity::class,
            parentColumns = ["id"],
            childColumns = ["txn_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = InvestmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["investment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["investment_id"])],
)
data class InvestmentLinkEntity(
    @PrimaryKey @ColumnInfo(name = "txn_id") val txnId: String,
    @ColumnInfo(name = "investment_id") val investmentId: String,
    @ColumnInfo(name = "kind") val kind: InvestmentLinkKind,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)