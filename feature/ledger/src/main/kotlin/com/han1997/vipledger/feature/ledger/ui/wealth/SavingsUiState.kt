package com.han1997.vipledger.feature.ledger.ui.wealth

import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

data class GoalEditor(val id: String? = null, val name: String = "", val target: String = "", val accountId: String = "", val deadline: String = "", val accountLocked: Boolean = false) {
    val amount: Money? get() = Money.parse(target)?.takeIf { it.cents > 0 }
    val date: LocalDate? get() = runCatching { LocalDate.parse(deadline) }.getOrNull()
    val valid: Boolean get() = name.isNotBlank() && amount != null && accountId.isNotBlank() && (deadline.isBlank() || date != null)
}
data class SavingsMovement(val goalId: String, val deposit: Boolean, val otherAccountId: String = "", val amount: String = "")
data class SavingsUiState(
    val loading: Boolean = true, val failed: Boolean = false,
    val goals: List<SavingsProgress> = emptyList(), val accounts: List<Account> = emptyList(),
    val selectedId: String? = null, val showArchived: Boolean = false,
    val editor: GoalEditor? = null, val movement: SavingsMovement? = null,
    val busy: Boolean = false, val writeFailed: Boolean = false,
) {
    val availableAccounts: List<Account> get() = accounts.filter { it.isUsable && !it.isDebt }
}
sealed interface SavingsAction {
    data object Retry : SavingsAction
    data object Create : SavingsAction
    data class Select(val id: String) : SavingsAction
    data class Edit(val id: String) : SavingsAction
    data class EditorChanged(val editor: GoalEditor) : SavingsAction
    data object Save : SavingsAction
    data object Dismiss : SavingsAction
    data object ToggleArchived : SavingsAction
    data class Archive(val id: String, val archived: Boolean) : SavingsAction
    data class Move(val id: String, val deposit: Boolean) : SavingsAction
    data class MovementChanged(val movement: SavingsMovement) : SavingsAction
    data class ConfirmMovement(val title: String) : SavingsAction
}
