package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.money.Money

/** 新建 / 编辑资金账户的入参；id 与时间戳由 SaveAccountUseCase 生成。 */
data class AccountDraft(
    val ledgerId: String,
    val name: String,
    val type: AccountType,
    val iconKey: String? = null,
    val initialBalance: Money = Money.ZERO,
    val creditLimit: Money? = null,
    val billingDay: Int? = null,
    val dueDay: Int? = null,
    val includeInNetWorth: Boolean = true,
    val remark: String? = null,
)