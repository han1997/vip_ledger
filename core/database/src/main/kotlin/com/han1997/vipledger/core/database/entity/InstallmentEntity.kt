package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus

/** 分期计划（docs/DATA-MODEL.md 3.7）。`*_date` 列存 UTC 零点毫秒。 */
@Entity(
    tableName = "installment_plan",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "status"]),
        Index(value = ["account_id"]),
    ],
)
data class InstallmentPlanEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "method") val method: InstallmentMethod,
    @ColumnInfo(name = "principal_cents") val principalCents: Long,
    @ColumnInfo(name = "periods") val periods: Int,
    @ColumnInfo(name = "annual_rate_bp") val annualRateBp: Int = 0,
    @ColumnInfo(name = "upfront_fee_cents") val upfrontFeeCents: Long = 0L,
    @ColumnInfo(name = "per_period_fee_cents") val perPeriodFeeCents: Long = 0L,
    @ColumnInfo(name = "first_due_date") val firstDueDate: Long,
    @ColumnInfo(name = "due_day") val dueDay: Int? = null,
    @ColumnInfo(name = "status") val status: InstallmentPlanStatus = InstallmentPlanStatus.ACTIVE,
    @ColumnInfo(name = "remark") val remark: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** 分期单期。 */
@Entity(
    tableName = "installment_period",
    foreignKeys = [
        ForeignKey(
            entity = InstallmentPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["plan_id", "period_no"], unique = true)],
)
data class InstallmentPeriodEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "period_no") val periodNo: Int,
    @ColumnInfo(name = "due_date") val dueDate: Long,
    @ColumnInfo(name = "principal_cents") val principalCents: Long,
    @ColumnInfo(name = "interest_cents") val interestCents: Long,
    @ColumnInfo(name = "fee_cents") val feeCents: Long = 0L,
    @ColumnInfo(name = "total_cents") val totalCents: Long,
    @ColumnInfo(name = "status") val status: InstallmentPeriodStatus = InstallmentPeriodStatus.PENDING,
    @ColumnInfo(name = "paid_txn_id") val paidTxnId: String? = null,
    @ColumnInfo(name = "paid_at") val paidAt: Long? = null,
)