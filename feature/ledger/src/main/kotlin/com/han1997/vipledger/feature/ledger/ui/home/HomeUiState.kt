package com.han1997.vipledger.feature.ledger.ui.home

import com.han1997.vipledger.core.domain.model.HomeOverview
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.Reminder
import kotlinx.datetime.LocalDate

/**
 * 首页状态；[overview] 为 null 表示还没有账本或数据还没到。
 *
 * 三个名字查表用于「最近账目」的副标题（账目只存 id）。
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val ledgerId: String? = null,
    val ledgerName: String = "",
    val ledgers: List<Ledger> = emptyList(),
    val overview: HomeOverview? = null,
    val reminders: List<Reminder> = emptyList(),
    val pendingDraftCount: Int = 0,
    val pendingAutoCount: Int = 0,
    val budget: com.han1997.vipledger.core.domain.model.BudgetReport? = null,
    val cards: List<com.han1997.vipledger.core.domain.model.HomeCardSetting> = com.han1997.vipledger.core.domain.model.HomeCardLayout.defaults(),
    val savings: List<com.han1997.vipledger.core.domain.model.SavingsProgress> = emptyList(),
    val accountNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val tagNames: Map<String, String> = emptyMap(),
    /** 设备时区「今天」（本地日流），hero 年月导语与「今天/昨天」分组标签用。 */
    val today: LocalDate? = null,
)

sealed interface HomeAction {
    data object Retry : HomeAction
    data class SelectLedger(val ledgerId: String) : HomeAction
}
