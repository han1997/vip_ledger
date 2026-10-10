package com.han1997.vipledger.core.model

/** 分期计息方式（docs/DATA-MODEL.md 3.7）。 */
enum class InstallmentMethod {
    /** 等额本息：每期还款总额相同。 */
    EQUAL_INSTALLMENT,

    /** 等额本金：每期本金相同，利息递减。 */
    EQUAL_PRINCIPAL,

    /** 先息后本：前 n-1 期只还利息，末期还本金。 */
    INTEREST_FIRST,

    /** 自定义每期金额（由用户逐期录入，计算器只做校验）。 */
    CUSTOM,
}

/** 分期计划状态。 */
enum class InstallmentPlanStatus { ACTIVE, CLOSED }

/** 分期单期状态。 */
enum class InstallmentPeriodStatus { PENDING, PARTIAL, PAID }

/** 计划来源：已有负债（不再重复计本金）或新放款（首期同事务发放本金）。 */
enum class InstallmentOrigin { EXISTING, DISBURSEMENT }

/**
 * 还款流水的分账口径：本金转账不计收支，利息与手续费计入支出。
 *
 * [PREPAY] 是部分提前还款的本金流水，会连带重算未还期次，因此不可单独改金额或删除。
 */
enum class InstallmentPostingKind { PRINCIPAL, INTEREST, FEE, PREPAY }
