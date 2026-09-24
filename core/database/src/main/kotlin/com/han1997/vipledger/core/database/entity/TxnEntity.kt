package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource

/**
 * 账目流水（docs/DATA-MODEL.md 3.5）。
 *
 * `amount_cents` 对除 `ADJUST` 外的类型恒为正；`import_hash` 上的唯一索引在 SQLite 里
 * 允许多个 NULL，刚好等价于「只对非空值去重」。
 */
@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "occurred_at"], orders = [Index.Order.ASC, Index.Order.DESC]),
        Index(value = ["ledger_id", "category_id", "occurred_at"]),
        Index(value = ["ledger_id", "type", "occurred_at"]),
        Index(value = ["account_id", "occurred_at"]),
        Index(value = ["to_account_id"]),
        Index(value = ["recurring_rule_id"]),
        Index(value = ["installment_plan_id", "installment_period_no"]),
        Index(value = ["ledger_id", "draft_status"]),
        Index(value = ["import_hash"], unique = true),
    ],
)
data class TxnEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "type") val type: TransactionType,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "remark") val remark: String = "",
    @ColumnInfo(name = "source") val source: TxnSource = TxnSource.MANUAL,
    @ColumnInfo(name = "draft_status") val draftStatus: DraftStatus = DraftStatus.CONFIRMED,
    @ColumnInfo(name = "exclude_from_stats") val excludeFromStats: Boolean = false,
    @ColumnInfo(name = "recurring_rule_id") val recurringRuleId: String? = null,
    @ColumnInfo(name = "installment_plan_id") val installmentPlanId: String? = null,
    @ColumnInfo(name = "installment_period_no") val installmentPeriodNo: Int? = null,
    @ColumnInfo(name = "import_hash") val importHash: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** 带上标签关联的流水读模型（`@Relation` 在 DAO 里用）。 */
data class TxnWithTagIds(
    @androidx.room.Embedded val txn: TxnEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "txn_id") val tagLinks: List<TxnTagEntity>,
)