package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.money.Money

/** 「重算余额」工具的单账户结果（docs/DATA-MODEL.md 第 6 节）。 */
data class AccountBalanceDiff(
    val accountId: String,
    val accountName: String,
    val cached: Money,
    val computed: Money,
) {
    val delta: Money get() = computed - cached

    val isConsistent: Boolean get() = delta.isZero
}

/** 重算汇总：用于设置页提示「已修正 N 个账户」。 */
data class RecomputeReport(
    val checked: Int,
    val fixed: List<AccountBalanceDiff>,
)