package com.han1997.vipledger.feature.ledger.ui.budget

import com.han1997.vipledger.core.domain.model.BudgetReport
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

data class BudgetOption(val id: String, val name: String)
data class BudgetEditor(val scopeKey: String, val input: String) {
    val amount: Money? get() = Money.parse(input)?.takeIf { it.cents >= 0 }
}
data class BudgetUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val month: LocalDate = LocalDate(1970, 1, 1),
    val report: BudgetReport? = null,
    val options: List<BudgetOption> = emptyList(),
    val editor: BudgetEditor? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)
sealed interface BudgetAction {
    data class MoveMonth(val delta: Int) : BudgetAction
    data object Today : BudgetAction
    data object Retry : BudgetAction
    data class Edit(val scopeKey: String) : BudgetAction
    data class SelectScope(val scopeKey: String) : BudgetAction
    data class Input(val text: String) : BudgetAction
    data object Save : BudgetAction
    data object Disable : BudgetAction
    data object Dismiss : BudgetAction
}
