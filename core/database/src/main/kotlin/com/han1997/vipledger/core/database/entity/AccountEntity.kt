package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.AccountType

/** 资金账户（docs/DATA-MODEL.md 3.2）。[currentBalanceCents] 是缓存，由流水事务维护。 */
@Entity(
    tableName = "account",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "sort_order"]),
        Index(value = ["type"]),
    ],
)
data class AccountEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: AccountType,
    @ColumnInfo(name = "icon_key") val iconKey: String? = null,
    @ColumnInfo(name = "color_argb") val colorArgb: Int? = null,
    @ColumnInfo(name = "initial_balance_cents") val initialBalanceCents: Long = 0L,
    @ColumnInfo(name = "current_balance_cents") val currentBalanceCents: Long = 0L,
    @ColumnInfo(name = "credit_limit_cents") val creditLimitCents: Long? = null,
    @ColumnInfo(name = "billing_day") val billingDay: Int? = null,
    @ColumnInfo(name = "due_day") val dueDay: Int? = null,
    @ColumnInfo(name = "include_in_net_worth") val includeInNetWorth: Boolean = true,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "remark") val remark: String? = null,
    @ColumnInfo(name = "card_no") val cardNo: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)