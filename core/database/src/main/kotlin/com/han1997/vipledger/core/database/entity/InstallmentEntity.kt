package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentOrigin
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.InstallmentPostingKind

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
    @ColumnInfo(name = "origin") val origin: InstallmentOrigin = InstallmentOrigin.EXISTING,
    @ColumnInfo(name = "disbursement_account_id") val disbursementAccountId: String? = null,
    @ColumnInfo(name = "disbursed_at") val disbursedAt: Long? = null,
    @ColumnInfo(name = "status") val status: InstallmentPlanStatus = InstallmentPlanStatus.ACTIVE,
    @ColumnInfo(name = "remark") val remark: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** 分期单期；`paid_*` 是累计已还的分账金额（支持部分还款）。 */
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
    @ColumnInfo(name = "paid_principal_cents") val paidPrincipalCents: Long = 0L,
    @ColumnInfo(name = "paid_interest_cents") val paidInterestCents: Long = 0L,
    @ColumnInfo(name = "paid_fee_cents") val paidFeeCents: Long = 0L,
    @ColumnInfo(name = "paid_txn_id") val paidTxnId: String? = null,
    @ColumnInfo(name = "paid_at") val paidAt: Long? = null,
)

/** 还款流水与计划/期次/分账口径的关联。 */
@Entity(
    tableName = "installment_link",
    foreignKeys = [
        ForeignKey(
            entity = TxnEntity::class,
            parentColumns = ["id"],
            childColumns = ["txn_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = InstallmentPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["plan_id", "period_no"]),
    ],
)
data class InstallmentLinkEntity(
    @PrimaryKey @ColumnInfo(name = "txn_id") val txnId: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "period_no") val periodNo: Int,
    @ColumnInfo(name = "kind") val kind: InstallmentPostingKind,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)