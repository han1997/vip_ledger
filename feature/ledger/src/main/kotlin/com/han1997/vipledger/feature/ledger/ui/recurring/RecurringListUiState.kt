package com.han1997.vipledger.feature.ledger.ui.recurring

import com.han1997.vipledger.core.domain.model.RecurringRule

/** 周期规则列表状态。 */
data class RecurringListUiState(
    val isLoading: Boolean = true,
    val ledgerId: String = "",
    val rules: List<RecurringRule> = emptyList(),
    val accountNames: Map<String, String> = emptyMap(),
    val generating: Boolean = false,
    val pendingDelete: RecurringRule? = null,
    val notice: RecurringNotice? = null,
    val noticeCount: Int? = null,
    val noticeDetail: String? = null,
)

/** 一次性提示：只放枚举，文案在界面层查表（见 .trellis/spec/android/ui.md）。 */
enum class RecurringNotice {
    GENERATED,
    NOTHING_DUE,
    ENABLED,
    PAUSED,
    DELETED,
    FAILED,
}

sealed interface RecurringListAction {
    data object GenerateNow : RecurringListAction

    data class ToggleEnabled(val rule: RecurringRule) : RecurringListAction

    data class RequestDelete(val rule: RecurringRule) : RecurringListAction

    data object ConfirmDelete : RecurringListAction

    data object DismissDelete : RecurringListAction

    data object ConsumeNotice : RecurringListAction
}
