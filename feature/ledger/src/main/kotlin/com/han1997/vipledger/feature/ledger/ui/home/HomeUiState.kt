package com.han1997.vipledger.feature.ledger.ui.home

import com.han1997.vipledger.core.domain.model.HomeOverview
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.Reminder

/**
 * 首页状态；[overview] 为 null 表示还没有账本或数据还没到。
 *
 * 三个名字查表用于「最近账目」的副标题（账目只存 id）。
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val ledgerId: String? = null,
    val ledgerName: String = "",
    val ledgers: List<Ledger> = emptyList(),
    val overview: HomeOverview? = null,
    val reminders: List<Reminder> = emptyList(),
    val pendingDraftCount: Int = 0,
    val accountNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val tagNames: Map<String, String> = emptyMap(),
)

sealed interface HomeAction {
    data class SelectLedger(val ledgerId: String) : HomeAction
}