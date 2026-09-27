package com.han1997.vipledger.feature.ledger.ui.drafts

import com.han1997.vipledger.core.domain.model.Txn

/**
 * 待确认草稿页状态。
 *
 * 草稿是周期规则 / 以后的 AI 记账生成、还没确认入账的账目；只有确认后才进统计与余额。
 * 文案查表用的 `*Names` 与首页一致（账目只存 id）。
 */
data class DraftListUiState(
    val isLoading: Boolean = true,
    val ledgerId: String = "",
    val drafts: List<Txn> = emptyList(),
    val accountNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val tagNames: Map<String, String> = emptyMap(),
    val busyIds: Set<String> = emptySet(),
    val confirmingAll: Boolean = false,
    val notice: DraftNotice? = null,
    val noticeDetail: String? = null,
)

/** 一次性提示：界面层查表成文案。 */
enum class DraftNotice { CONFIRMED, DISCARDED, FAILED }

sealed interface DraftAction {
    data class Confirm(val txnId: String) : DraftAction

    data class Discard(val txnId: String) : DraftAction

    data object ConfirmAll : DraftAction

    data object ConsumeNotice : DraftAction
}
