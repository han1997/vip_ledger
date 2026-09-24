package com.han1997.vipledger.feature.ledger.ui.manage

import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.feature.ledger.ui.common.ManageNotice

data class CategoryManageUiState(
    val isLoading: Boolean = true,
    val nodes: List<CategoryNode> = emptyList(),
    val editor: CategoryEditor? = null,
    val pendingDelete: Category? = null,
    val notice: ManageNotice? = null,
    /** 失败原因（来自 AppError.message）；成功或没有说明时为 null。 */
    val noticeDetail: String? = null,
) {
    fun nodesOf(kind: CategoryKind): List<CategoryNode> = nodes.filter { it.category.kind == kind }
}

/**
 * 分类新建/编辑表单。[categoryId] 为 null 表示新建；
 * 新建子分类时 [parentId] 非空且 [kind] 跟随父分类锁定。
 */
data class CategoryEditor(
    val categoryId: String? = null,
    val parentId: String? = null,
    val name: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
) {
    val isChild: Boolean get() = parentId != null
}

sealed interface CategoryManageAction {
    data class OpenCreateTop(val kind: CategoryKind) : CategoryManageAction

    data class OpenCreateChild(val parent: Category) : CategoryManageAction

    data class OpenEdit(val category: Category) : CategoryManageAction

    data class UpdateName(val text: String) : CategoryManageAction

    data class UpdateKind(val kind: CategoryKind) : CategoryManageAction

    data object DismissEditor : CategoryManageAction

    data object SaveEditor : CategoryManageAction

    data class RequestDelete(val category: Category) : CategoryManageAction

    data object ConfirmDelete : CategoryManageAction

    data object DismissDelete : CategoryManageAction

    data object ConsumeNotice : CategoryManageAction
}