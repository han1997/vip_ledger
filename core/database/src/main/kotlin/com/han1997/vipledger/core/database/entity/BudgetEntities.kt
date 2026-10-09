package com.han1997.vipledger.core.database.entity

import androidx.room.*

@Entity(tableName = "budget_limit", foreignKeys = [ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledger_id"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["ledger_id", "month", "scope_key"], unique = true)])
data class BudgetLimitEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val month: String,
    @ColumnInfo(name = "scope_key") val scopeKey: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "budget_alert", primaryKeys = ["ledger_id", "month", "scope_key", "threshold"], foreignKeys = [ForeignKey(entity = LedgerEntity::class, parentColumns = ["id"], childColumns = ["ledger_id"], onDelete = ForeignKey.CASCADE)])
data class BudgetAlertEntity(
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    val month: String,
    @ColumnInfo(name = "scope_key") val scopeKey: String,
    val threshold: Int,
    @ColumnInfo(name = "notified_at") val notifiedAt: Long,
)
