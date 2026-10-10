package com.han1997.vipledger.feature.ledger.ui.investment

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.InvestmentProgress
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.money.Money

/** 新建理财：名称 + 资金账户 + 首次投入。 */
data class InvestmentEditor(
    val name: String = "",
    val cashAccountId: String = "",
    val amount: String = "",
    val remark: String = "",
) {
    val money: Money? get() = Money.parse(amount)?.takeIf { it.isPositive }

    val valid: Boolean get() = name.isNotBlank() && money != null && cashAccountId.isNotBlank()
}

/** 追加投入 / 赎回本金 / 登记收益 / 登记损失。 */
data class InvestmentOperation(
    val investmentId: String,
    val kind: InvestmentLinkKind,
    val cashAccountId: String = "",
    val amount: String = "",
) {
    val money: Money? get() = Money.parse(amount)?.takeIf { it.isPositive }
}

data class InvestmentUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val investments: List<InvestmentProgress> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val showClosed: Boolean = false,
    val editor: InvestmentEditor? = null,
    val operation: InvestmentOperation? = null,
    val busy: Boolean = false,
    val writeFailed: Boolean = false,
    val errorKey: String? = null,
) {
    val availableAccounts: List<Account> get() = accounts.filter { it.isUsable && !it.isDebt }

    val visible: List<InvestmentProgress> get() = investments.filter { showClosed || it.active }

    val outstandingTotal: Money get() = Money.sum(visible.map { it.summary.principalOutstanding })

    val realizedTotal: Money get() = Money.sum(visible.map { it.summary.realizedNet })
}

sealed interface InvestmentAction {
    data object Retry : InvestmentAction

    data object Create : InvestmentAction

    data class EditorChanged(val editor: InvestmentEditor) : InvestmentAction

    data object Save : InvestmentAction

    data class Operate(val id: String, val kind: InvestmentLinkKind) : InvestmentAction

    data class OperationChanged(val form: InvestmentOperation) : InvestmentAction

    data class ConfirmOperation(val title: String) : InvestmentAction

    data class SetClosed(val id: String, val closed: Boolean) : InvestmentAction

    data object ToggleClosed : InvestmentAction

    data object Dismiss : InvestmentAction
}