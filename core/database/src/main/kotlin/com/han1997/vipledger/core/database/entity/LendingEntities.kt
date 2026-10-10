package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.LendingLinkKind

/** 借贷记录（docs/DATA-MODEL.md 3.8）。本金在资金与应收/应付账户间流转。 */
@Entity(
    tableName = "lending",
    foreignKeys = [
        ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledger_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("ledger_id"), Index("account_id")],
)
data class LendingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val direction: LendingDirection,
    val counterparty: String,
    @ColumnInfo(name = "principal_cents") val principalCents: Long,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    val remark: String,
    val closed: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 借贷关联流水：本金发放 / 结清 / 利息，一条流水只属于一个借贷记录。 */
@Entity(
    tableName = "lending_link",
    foreignKeys = [
        ForeignKey(entity = TxnEntity::class, parentColumns = ["id"], childColumns = ["txn_id"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = LendingEntity::class, parentColumns = ["id"], childColumns = ["lending_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("lending_id")],
)
data class LendingLinkEntity(
    @PrimaryKey @ColumnInfo(name = "txn_id") val txnId: String,
    @ColumnInfo(name = "lending_id") val lendingId: String,
    val kind: LendingLinkKind,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
