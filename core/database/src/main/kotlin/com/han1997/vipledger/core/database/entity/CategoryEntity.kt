package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.han1997.vipledger.core.model.CategoryKind

/** 分类（docs/DATA-MODEL.md 3.3）。两级：`parent_id` 为空即一级分类。 */
@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_id", "kind", "parent_id", "sort_order"]),
        Index(value = ["parent_id"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ledger_id") val ledgerId: String,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: CategoryKind,
    @ColumnInfo(name = "icon_key") val iconKey: String? = null,
    @ColumnInfo(name = "color_argb") val colorArgb: Int? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)