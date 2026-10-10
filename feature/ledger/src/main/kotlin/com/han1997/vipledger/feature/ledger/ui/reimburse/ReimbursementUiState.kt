package com.han1997.vipledger.feature.ledger.ui.reimburse

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.ReimbursementCalculator
import com.han1997.vipledger.core.domain.model.ReimbursementProgress
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.money.Money

/** 报销项目编辑器：名称 + 对象/备注 + 多笔费用。 */
data class ReimbursementEditor(
    val id: String? = null,
    val title: String = "",
    val counterparty: String = "",
    val remark: String = "",
    val itemIds: Set<String> = emptySet(),
) {
    val valid: Boolean get() = title.isNotBlank() && itemIds.isNotEmpty()
}

/** 到账登记：需选定原支出、到账账户与金额。 */
data class ReimbursementReceiptForm(
    val projectId: String,
    val itemTxnId: String = "",
    val accountId: String = "",
    val amount: String = "",
) {
    val money: Money? get() = Money.parse(amount)?.takeIf { it.isPositive }
}

data class ReimbursementUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val projects: List<ReimbursementProgress> = emptyList(),
    val expenses: List<Txn> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val selectedId: String? = null,
    val showClosed: Boolean = false,
    val editor: ReimbursementEditor? = null,
    val receipt: ReimbursementReceiptForm? = null,
    val busy: Boolean = false,
    val writeFailed: Boolean = false,
    val errorKey: String? = null,
) {
    val availableAccounts: List<Account> get() = accounts.filter { it.isUsable }

    val pendingTotal: Money get() = ReimbursementCalculator.pendingTotal(projects)

    /** 可加入某项目编辑器候选的支出：未被其他项目占用。 */
    fun candidatesFor(projectId: String?): List<Txn> {
        val owned = projects.flatMap { progress -> progress.items.map { it.id } }.toSet()
        val own = projects.firstOrNull { it.project.id == projectId }?.items?.map { it.id }?.toSet().orEmpty()
        return expenses.filter { it.id !in owned || it.id in own }
    }
}

sealed interface ReimbursementAction {
    data object Retry : ReimbursementAction
    data object Create : ReimbursementAction
    data class Select(val id: String) : ReimbursementAction
    data class Edit(val id: String) : ReimbursementAction
    data class EditorChanged(val editor: ReimbursementEditor) : ReimbursementAction
    data class ToggleItem(val txnId: String) : ReimbursementAction
    data object Save : ReimbursementAction
    data class Receive(val projectId: String, val itemTxnId: String) : ReimbursementAction
    data class ReceiptChanged(val form: ReimbursementReceiptForm) : ReimbursementAction
    data class ConfirmReceipt(val title: String) : ReimbursementAction
    data class SetClosed(val id: String, val closed: Boolean) : ReimbursementAction
    data object ToggleClosed : ReimbursementAction
    data object Dismiss : ReimbursementAction
}
