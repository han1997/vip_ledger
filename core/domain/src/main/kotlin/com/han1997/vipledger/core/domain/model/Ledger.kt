package com.han1997.vipledger.core.domain.model

/** 账本（docs/DATA-MODEL.md 3.1）。账本之间完全隔离：分类/标签/账户/规则都属账本级。 */
data class Ledger(
    val id: String,
    val name: String,
    val iconKey: String? = null,
    val currencyCode: String = DEFAULT_CURRENCY,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isArchived: Boolean get() = archivedAt != null

    companion object {
        const val DEFAULT_CURRENCY: String = "CNY"
    }
}