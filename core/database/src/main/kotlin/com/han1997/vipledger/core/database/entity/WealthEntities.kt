package com.han1997.vipledger.core.database.entity

import androidx.room.*

@Entity(tableName = "savings_goal", foreignKeys = [ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledger_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("ledger_id")])
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    val name: String,
    @ColumnInfo(name = "target_cents") val targetCents: Long,
    val deadline: String?,
    val archived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
@Entity(tableName = "savings_link", foreignKeys = [
    ForeignKey(entity = SavingsGoalEntity::class, parentColumns = ["id"], childColumns = ["goal_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TxnEntity::class, parentColumns = ["id"], childColumns = ["txn_id"], onDelete = ForeignKey.NO_ACTION),
], indices = [Index("goal_id")])
data class SavingsLinkEntity(@PrimaryKey @ColumnInfo(name = "txn_id") val txnId: String, @ColumnInfo(name = "goal_id") val goalId: String, @ColumnInfo(name = "created_at") val createdAt: Long)

@Entity(tableName = "home_card", primaryKeys = ["ledger_id", "card_key"], foreignKeys = [ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledger_id"], onDelete = ForeignKey.CASCADE)])
data class HomeCardEntity(@ColumnInfo(name = "ledger_id") val ledgerId: String, @ColumnInfo(name = "card_key") val cardKey: String, val position: Int, val visible: Boolean)

@Entity(tableName = "asset_baseline", foreignKeys = [ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledger_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("ledger_id")])
data class AssetBaselineEntity(@PrimaryKey @ColumnInfo(name = "account_id") val accountId: String, @ColumnInfo(name = "ledger_id") val ledgerId: String, @ColumnInfo(name = "captured_at") val capturedAt: Long, @ColumnInfo(name = "balance_cents") val balanceCents: Long, val included: Boolean)
