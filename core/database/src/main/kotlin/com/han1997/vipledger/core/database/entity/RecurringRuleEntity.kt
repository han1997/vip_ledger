package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType

/** 周期记账规则（docs/DATA-MODEL.md 3.6）。[tagIdsJson] 是标签 id 的 JSON 数组。 */
@Entity(
    tableName = "recurring_rule",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "enabled", "next_occurrence_at"]),
        Index(value = ["account_id"]),
        Index(value = ["category_id"]),
    ],
)
data class RecurringRuleEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "txn_type") val txnType: TransactionType,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "tag_ids_json") val tagIdsJson: String? = null,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "remark") val remark: String = "",
    @ColumnInfo(name = "freq") val freq: RecurrenceFrequency,
    @ColumnInfo(name = "interval") val interval: Int = 1,
    @ColumnInfo(name = "by_weekday") val byWeekday: Int? = null,
    @ColumnInfo(name = "by_month_day") val byMonthDay: Int? = null,
    @ColumnInfo(name = "by_month") val byMonth: Int? = null,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_type") val endType: RecurrenceEndType = RecurrenceEndType.NEVER,
    @ColumnInfo(name = "end_date") val endDate: Long? = null,
    @ColumnInfo(name = "max_occurrences") val maxOccurrences: Int? = null,
    @ColumnInfo(name = "generated_count") val generatedCount: Int = 0,
    @ColumnInfo(name = "next_occurrence_at") val nextOccurrenceAt: Long? = null,
    @ColumnInfo(name = "last_generated_at") val lastGeneratedAt: Long? = null,
    @ColumnInfo(name = "generate_mode") val generateMode: GenerateMode = GenerateMode.DRAFT,
    @ColumnInfo(name = "missed_policy") val missedPolicy: MissedPolicy = MissedPolicy.SKIP,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)