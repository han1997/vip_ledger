package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.money.Money

/**
 * 资金账户（docs/DATA-MODEL.md 3.2）。
 *
 * [currentBalance] 是缓存值，恒等于「期初 + 流水净额」，方向与资产负债表一致：
 * 负债账户欠钱时为负值，展示层再按 [AccountType.isDebt] 显示成欠款金额。
 */
data class Account(
    val id: String,
    val ledgerId: String,
    val name: String,
    val type: AccountType,
    val iconKey: String? = null,
    val colorArgb: Int? = null,
    val initialBalance: Money = Money.ZERO,
    val currentBalance: Money = Money.ZERO,
    val creditLimit: Money? = null,
    val billingDay: Int? = null,
    val dueDay: Int? = null,
    val includeInNetWorth: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val remark: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isDebt: Boolean get() = type.isDebt

    /** 负债账户的欠款金额（正数表示还欠这么多）。 */
    val debtAmount: Money get() = if (isDebt) -currentBalance else currentBalance

    val isUsable: Boolean get() = deletedAt == null && !isArchived
}