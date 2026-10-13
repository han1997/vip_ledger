package com.han1997.vipledger.feature.ledger.ui.manage

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.feature.ledger.ui.common.ManageNotice

data class AccountManageUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val showArchived: Boolean = false,
    val editor: AccountEditor? = null,
    val pendingDelete: Account? = null,
    val notice: ManageNotice? = null,
    /** 失败原因（来自 AppError.message）；成功或没有说明时为 null。 */
    val noticeDetail: String? = null,
)

/** 账户新建/编辑表单。[accountId] 为 null 表示新建。金额用字符串存，保存时才解析。 */
data class AccountEditor(
    val accountId: String? = null,
    val name: String = "",
    val type: AccountType = AccountType.CASH,
    val initialBalanceInput: String = "",
    val creditLimitInput: String = "",
    val billingDayInput: String = "",
    val dueDayInput: String = "",
    val cardNoInput: String = "",
    val includeInNetWorth: Boolean = true,
)

sealed interface AccountManageAction {
    data object OpenCreate : AccountManageAction

    data class OpenEdit(val account: Account) : AccountManageAction

    data class UpdateEditorName(val text: String) : AccountManageAction

    data class UpdateEditorType(val type: AccountType) : AccountManageAction

    data class UpdateInitialBalance(val text: String) : AccountManageAction

    data class UpdateCreditLimit(val text: String) : AccountManageAction

    data class UpdateBillingDay(val text: String) : AccountManageAction

    data class UpdateDueDay(val text: String) : AccountManageAction

    data class UpdateCardNo(val text: String) : AccountManageAction

    data class UpdateIncludeInNetWorth(val include: Boolean) : AccountManageAction

    data object DismissEditor : AccountManageAction

    data object SaveEditor : AccountManageAction

    data class SetArchived(val accountId: String, val archived: Boolean) : AccountManageAction

    data class RequestDelete(val account: Account) : AccountManageAction

    data object ConfirmDelete : AccountManageAction

    data object DismissDelete : AccountManageAction

    data object ToggleShowArchived : AccountManageAction

    data object ConsumeNotice : AccountManageAction
}