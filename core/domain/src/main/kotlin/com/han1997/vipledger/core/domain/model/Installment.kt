package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/** 分期计划（docs/DATA-MODEL.md 3.7）。[annualRateBp] 是年化基点，1800 表示 18%。 */
data class InstallmentPlan(
    val id: String,
    val ledgerId: String,
    val accountId: String,
    val name: String,
    val method: InstallmentMethod,
    val principal: Money,
    val periods: Int,
    val annualRateBp: Int = 0,
    val upfrontFee: Money = Money.ZERO,
    val perPeriodFee: Money = Money.ZERO,
    val firstDueDate: LocalDate,
    val dueDay: Int? = null,
    val status: InstallmentPlanStatus = InstallmentPlanStatus.ACTIVE,
    val remark: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** 分期单期。金额由 [com.han1997.vipledger.core.domain.calculator.InstallmentCalculator] 生成。 */
data class InstallmentPeriod(
    val id: String,
    val planId: String,
    val periodNo: Int,
    val dueDate: LocalDate,
    val principal: Money,
    val interest: Money,
    val fee: Money = Money.ZERO,
    val status: InstallmentPeriodStatus = InstallmentPeriodStatus.PENDING,
    val paidTxnId: String? = null,
    val paidAt: Long? = null,
) {
    val total: Money get() = principal + interest + fee

    val isPaid: Boolean get() = status == InstallmentPeriodStatus.PAID
}