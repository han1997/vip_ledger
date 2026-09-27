package com.han1997.vipledger.feature.ledger.ui.loan

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.usecase.loan.InstallmentPlanDetail

/** 分期计划详情状态：计划 + 各期明细 + 还款对话框。 */
data class LoanDetailUiState(
    val isLoading: Boolean = true,
    val planId: String = "",
    val ledgerId: String = "",
    val detail: InstallmentPlanDetail? = null,
    val accountNames: Map<String, String> = emptyMap(),
    /** 可选的还款来源账户（已排除负债账户本身）。 */
    val repayAccounts: List<Account> = emptyList(),
    val repayPeriodNo: Int? = null,
    val repayAccountId: String? = null,
    val repaying: Boolean = false,
    val notice: LoanNotice? = null,
    val noticeDetail: String? = null,
)

sealed interface LoanDetailAction {
    data class StartRepay(val periodNo: Int) : LoanDetailAction

    data class SelectRepayAccount(val accountId: String) : LoanDetailAction

    data object ConfirmRepay : LoanDetailAction

    data object CancelRepay : LoanDetailAction

    data object ConsumeNotice : LoanDetailAction
}
