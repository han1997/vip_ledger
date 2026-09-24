package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.CategoryKind

/** 分类（docs/DATA-MODEL.md 3.3）。固定两级：[parentId] 为空是一级分类，非空时其父必须也是一级。 */
data class Category(
    val id: String,
    val ledgerId: String,
    val parentId: String? = null,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String? = null,
    val colorArgb: Int? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isTopLevel: Boolean get() = parentId == null
}