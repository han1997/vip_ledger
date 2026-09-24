package com.han1997.vipledger.feature.ledger.ui.manage

import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.feature.ledger.ui.common.ManageNotice

data class LedgerManageUiState(
    val isLoading: Boolean = true,
    val ledgers: List<Ledger> = emptyList(),
    val currentLedgerId: String? = null,
    val editor: LedgerEditor? = null,
    val pendingDelete: Ledger? = null,
    val notice: ManageNotice? = null,
    /** 失败原因（来自 AppError.message），成功或无语义时为 null。 */
    val noticeDetail: String? = null,
)

/** 账本新建/改名弹窗的表单。[ledgerId] 为 null 表示新建。 */
data class LedgerEditor(
    val ledgerId: String? = null,
    val name: String = "",
)

sealed interface LedgerManageAction {
    data object OpenCreate : LedgerManageAction

    data class OpenEdit(val ledger: Ledger) : LedgerManageAction

    data class UpdateEditorName(val text: String) : LedgerManageAction

    data object DismissEditor : LedgerManageAction

    data object SaveEditor : LedgerManageAction

    data class Select(val ledgerId: String) : LedgerManageAction

    data class SetDefault(val ledgerId: String) : LedgerManageAction

    data class SetArchived(val ledgerId: String, val archived: Boolean) : LedgerManageAction

    data class RequestDelete(val ledger: Ledger) : LedgerManageAction

    data object ConfirmDelete : LedgerManageAction

    data object DismissDelete : LedgerManageAction

    data object ConsumeNotice : LedgerManageAction
}