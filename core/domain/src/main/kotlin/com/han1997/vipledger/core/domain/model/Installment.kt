package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentOrigin
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.InstallmentPostingKind
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/**
 * 分期计划（docs/DATA-MODEL.md 3.7）。[annualRateBp] 是年化基点，1800 表示 18%。
 *
 * [origin] 区分「已有负债」与「新放款」：只有新放款才在创建时发放本金，避免已有负债重复计本金。
 * 「部分提前还款」会下调 [principal]，同时等额下调未还期次的本金，保持「各期本金之和 = principal」。
 */
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
    val origin: InstallmentOrigin = InstallmentOrigin.EXISTING,
    val disbursementAccountId: String? = null,
    val disbursedAt: Long? = null,
    val status: InstallmentPlanStatus = InstallmentPlanStatus.ACTIVE,
    val remark: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isDisbursement: Boolean get() = origin == InstallmentOrigin.DISBURSEMENT
}

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
    val paidPrincipal: Money = Money.ZERO,
    val paidInterest: Money = Money.ZERO,
    val paidFee: Money = Money.ZERO,
    val paidTxnId: String? = null,
    val paidAt: Long? = null,
) {
    val total: Money get() = principal + interest + fee

    val paidTotal: Money get() = paidPrincipal + paidInterest + paidFee

    val remainingTotal: Money get() = total - paidTotal

    val remainingPrincipal: Money get() = principal - paidPrincipal

    val isPaid: Boolean get() = status == InstallmentPeriodStatus.PAID

    val isPartiallyPaid: Boolean get() = status == InstallmentPeriodStatus.PARTIAL

    /** 完全未还：只有这种期次允许被改利率或提前还款重算。 */
    val isUntouched: Boolean get() = status == InstallmentPeriodStatus.PENDING && paidTotal.isZero
}

/**
 * 一次还款/核销在某一期上的分账结果。
 *
 * 金额为 0 的口径不生成流水，因此列表里不会出现 0 项。
 */
data class InstallmentAllocation(
    val periodNo: Int,
    val kind: InstallmentPostingKind,
    val amount: Money,
)

/** 还款流水与计划/期次/口径的关联，供守卫与详情页读取。 */
data class InstallmentLink(
    val txnId: String,
    val planId: String,
    val periodNo: Int,
    val kind: InstallmentPostingKind,
    val createdAt: Long,
)