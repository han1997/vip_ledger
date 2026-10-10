package com.han1997.vipledger.feature.ledger.ui.lending

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.LendingProgress
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/** 新建借贷：方向 + 对方 + 本金 + 资金账户 + 可选到期日。 */
data class LendingEditor(
    val direction: LendingDirection = LendingDirection.LEND,
    val counterparty: String = "",
    val principal: String = "",
    val cashAccountId: String = "",
    val dueDate: String = "",
    val remark: String = "",
) {
    val amount: Money? get() = Money.parse(principal)?.takeIf { it.isPositive }

    val date: LocalDate? get() = if (dueDate.isBlank()) null else runCatching { LocalDate.parse(dueDate) }.getOrNull()

    val valid: Boolean
        get() = counterparty.isNotBlank() && amount != null && cashAccountId.isNotBlank() && (dueDate.isBlank() || date != null)
}

/** 结清或登记利息。 */
data class LendingSettlement(
    val lendingId: String,
    val interest: Boolean,
    val cashAccountId: String = "",
    val amount: String = "",
) {
    val money: Money? get() = Money.parse(amount)?.takeIf { it.isPositive }
}

data class LendingUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val lendings: List<LendingProgress> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val selectedId: String? = null,
    val showClosed: Boolean = false,
    val editor: LendingEditor? = null,
    val settlement: LendingSettlement? = null,
    val busy: Boolean = false,
    val writeFailed: Boolean = false,
    val errorKey: String? = null,
) {
    val availableAccounts: List<Account> get() = accounts.filter { it.isUsable && !it.isDebt }

    val outstandingTotal: Money get() = Money.sum(lendings.filter { it.active }.map { it.outstanding })
}

sealed interface LendingAction {
    data object Retry : LendingAction
    data object Create : LendingAction
    data class Select(val id: String) : LendingAction
    data class EditorChanged(val editor: LendingEditor) : LendingAction
    data object Save : LendingAction
    data class Settle(val id: String) : LendingAction
    data class Interest(val id: String) : LendingAction
    data class SettlementChanged(val form: LendingSettlement) : LendingAction
    data class ConfirmSettlement(val title: String) : LendingAction
    data class SetClosed(val id: String, val closed: Boolean) : LendingAction
    data object ToggleClosed : LendingAction
    data object Dismiss : LendingAction
}
