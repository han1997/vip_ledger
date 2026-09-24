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
enum class InstallmentPeriodStatus { PENDING, PAID }