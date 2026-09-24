package com.han1997.vipledger.feature.ledger.ui.manage

import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.feature.ledger.ui.common.ManageNotice

data class TagManageUiState(
    val isLoading: Boolean = true,
    val tags: List<Tag> = emptyList(),
    val editor: TagEditor? = null,
    val pendingDelete: Tag? = null,
    val notice: ManageNotice? = null,
    /** 失败原因（来自 AppError.message）；成功或没有说明时为 null。 */
    val noticeDetail: String? = null,
)

/** 标签新建/改名表单。[tagId] 为 null 表示新建。 */
data class TagEditor(
    val tagId: String? = null,
    val name: String = "",
)

sealed interface TagManageAction {
    data object OpenCreate : TagManageAction

    data class OpenEdit(val tag: Tag) : TagManageAction

    data class UpdateName(val text: String) : TagManageAction

    data object DismissEditor : TagManageAction

    data object SaveEditor : TagManageAction

    data class RequestDelete(val tag: Tag) : TagManageAction

    data object ConfirmDelete : TagManageAction

    data object DismissDelete : TagManageAction

    data object ConsumeNotice : TagManageAction
}