package com.han1997.vipledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 账本（docs/DATA-MODEL.md 3.1）。 */
@Entity(
    tableName = "ledger",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["is_default"]),
    ],
)
data class LedgerEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "icon_key") val iconKey: String? = null,
    @ColumnInfo(name = "currency_code") val currencyCode: String = "CNY",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)