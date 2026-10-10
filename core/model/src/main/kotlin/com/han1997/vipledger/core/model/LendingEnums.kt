package com.han1997.vipledger.core.model

/** 借贷方向（docs/DATA-MODEL.md 3.8）。[accountType] 是本金在资产侧的落点账户类型。 */
enum class LendingDirection(val accountType: AccountType) {
    /** 借出：我方把钱借给别人，形成应收（资产）。 */
    LEND(AccountType.RECEIVABLE),

    /** 借入：我方向别人借钱，形成应付（负债）。 */
    BORROW(AccountType.PAYABLE),
    ;

    val isLend: Boolean get() = this == LEND
}

/** 借贷关联流水的用途。 */
enum class LendingLinkKind {
    /** 本金发放：资金账户与应收/应付账户之间的首次本金流转。 */
    DISBURSE,

    /** 结清：对方归还本金（借出）或我方归还本金（借入）。 */
    SETTLE,

    /** 利息：借出收息计入收入，借入付息计入支出。 */
    INTEREST,
}
