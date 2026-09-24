package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.CategoryKind

/** 分类树的节点：一级分类 + 其直接子分类（固定两级，见 docs/DATA-MODEL.md 3.3）。 */
data class CategoryNode(
    val category: Category,
    val children: List<Category> = emptyList(),
) {
    val id: String get() = category.id

    val name: String get() = category.name

    val kind: CategoryKind get() = category.kind

    val isArchived: Boolean get() = category.isArchived
}