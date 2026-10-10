package com.han1997.vipledger.feature.ledger.ui.wealth

import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.model.money.Money

data class AssetsUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val ledgerId: String? = null,
    val ledgerName: String = "",
    val report: AssetsReport? = null,
    val showArchived: Boolean = false,
    val reimbursementPending: Money = Money.ZERO,
    val lendingOutstanding: Money = Money.ZERO,
    val lendingActive: Int = 0,
)
sealed interface AssetsAction { data object ToggleArchived : AssetsAction; data object Retry : AssetsAction }
data class AccountDetailUiState(
    val loading: Boolean = true, val failed: Boolean = false,
    val account: Account? = null, val txns: List<Txn> = emptyList(),
    val accountNames: Map<String, String> = emptyMap(), val categoryNames: Map<String, String> = emptyMap(), val tagNames: Map<String, String> = emptyMap(),
    val baseline: AssetBaseline? = null, val balanceInput: String? = null, val busy: Boolean = false, val writeFailed: Boolean = false,
)
sealed interface AccountDetailAction {
    data object Retry : AccountDetailAction
    data object Adjust : AccountDetailAction
    data class Input(val text: String) : AccountDetailAction
    data class SaveAdjustment(val title: String) : AccountDetailAction
    data object Dismiss : AccountDetailAction
    data class Archive(val archived: Boolean) : AccountDetailAction
}
