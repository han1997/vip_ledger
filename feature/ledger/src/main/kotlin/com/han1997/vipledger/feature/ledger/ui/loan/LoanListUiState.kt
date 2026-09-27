package com.han1997.vipledger.feature.ledger.ui.loan

import com.han1997.vipledger.core.domain.usecase.loan.InstallmentSummary

/** 分期计划列表状态。 */
data class LoanListUiState(
    val isLoading: Boolean = true,
    val ledgerId: String = "",
    val summaries: List<InstallmentSummary> = emptyList(),
    val accountNames: Map<String, String> = emptyMap(),
    val deletingId: String? = null,
    val notice: LoanNotice? = null,
    val noticeDetail: String? = null,
)

/** 一次性提示：界面层查表成文案。 */
enum class LoanNotice { DELETED, REPAID, FAILED }

sealed interface LoanListAction {
    data class Delete(val planId: String) : LoanListAction

    data object ConsumeNotice : LoanListAction
}
