package com.han1997.vipledger.core.model

/** 账目类型。金额恒为正数，资金方向由类型决定（docs/DATA-MODEL.md 3.5）。 */
enum class TransactionType {
    EXPENSE,
    REFUND,
    INCOME,
    TRANSFER,
    DEBT_REPAY,
    ADJUST,
    ;

    /** 是否参与收支统计：只有支出与收入计入，转账/还款/调整不计（docs/DATA-MODEL.md 第 5 节）。 */
    val isAccountingForStats: Boolean
        get() = when (this) {
            EXPENSE, REFUND, INCOME -> true
            TRANSFER, DEBT_REPAY, ADJUST -> false
        }

    /** 是否需要目标账户（如转账的转入账户、还款的目标负债账户）。 */
    val requiresTargetAccount: Boolean
        get() = when (this) {
            TRANSFER, DEBT_REPAY -> true
            EXPENSE, REFUND, INCOME, ADJUST -> false
        }
}

/** 资金账户类型。 */
enum class AccountType {
    CASH,
    BANK_DEBIT,
    CREDIT_CARD,
    VIRTUAL,
    RECEIVABLE,
    PAYABLE,
    LOAN,
    INVESTMENT,
    PREPAID,
    ;

    /** 负债类账户：余额展示为欠款，参与净资产的负向计算。 */
    val isDebt: Boolean
        get() = when (this) {
            CREDIT_CARD, PAYABLE, LOAN -> true
            CASH, BANK_DEBIT, VIRTUAL, RECEIVABLE, INVESTMENT, PREPAID -> false
        }

    /** 是否需要填写账单日/还款日。 */
    val hasBillingCycle: Boolean
        get() = when (this) {
            CREDIT_CARD, LOAN -> true
            else -> false
        }
}

/** 分类所属的收支方向。 */
enum class CategoryKind { INCOME, EXPENSE, TRANSFER }

/** 账目草稿状态：AI/周期/导入产生的待确认记录不计入统计。 */
enum class DraftStatus { CONFIRMED, PENDING, DISCARDED }

/** 账目来源，用于筛选与审计。 */
enum class TxnSource { MANUAL, RECURRING, AI, IMPORT, RULE, AUTO }
