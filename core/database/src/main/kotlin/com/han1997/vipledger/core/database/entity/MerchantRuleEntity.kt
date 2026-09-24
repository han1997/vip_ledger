package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField

/** 商户/关键字 → 分类规则（docs/DATA-MODEL.md 3.8）。 */
@Entity(
    tableName = "merchant_rule",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "enabled", "priority"]),
        Index(value = ["category_id"]),
    ],
)
data class MerchantRuleEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "match_type") val matchType: RuleMatchType = RuleMatchType.CONTAINS,
    @ColumnInfo(name = "target_field") val targetField: RuleTargetField = RuleTargetField.ANY,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "priority") val priority: Int = 100,
    @ColumnInfo(name = "source") val source: RuleSource = RuleSource.USER,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "hit_count") val hitCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)