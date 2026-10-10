package com.han1997.vipledger.core.domain.usecase.loan

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.InstallmentPostingKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money

/**
 * 分期还款流水的改/删/还原守卫（在事务内调用）。
 *
 * - 分账口径（本金转账 / 利息手续费支出）与所属计划、目标负债账户不可改；
 * - 期次还款流水（本金/利息/手续费）改金额或删除后，会在同一事务里按存活流水重算该期累计与状态，
 *   超过该期应还即拒绝；
 * - 部分提前还款流水承载计划重算，金额锁定且不可删除。
 */
class InstallmentTxnGuard(
    private val repository: InstallmentRepository,
    private val txns: TxnRepository,
    private val timeProvider: TimeProvider,
) {
    companion object {
        const val LINK_INVALID = "installment_link_invalid"
        const val DIRECTION = "installment_direction"
        const val AMOUNT_LOCKED = "installment_amount_locked"
        const val OVER_PAID = "installment_over_paid"
    }

    suspend fun validate(updated: Txn): AppError? {
        val link = repository.linkForTxn(updated.id) ?: return null
        val plan = repository.getPlan(link.planId) ?: return AppError.Validation(LINK_INVALID)
        if (updated.ledgerId != plan.ledgerId) return AppError.Validation(LINK_INVALID)
        val isPrincipal = link.kind == InstallmentPostingKind.PRINCIPAL || link.kind == InstallmentPostingKind.PREPAY
        if (updated.type != (if (isPrincipal) TransactionType.TRANSFER else TransactionType.EXPENSE)) {
            return AppError.Validation(DIRECTION)
        }
        if (isPrincipal && updated.toAccountId != plan.accountId) return AppError.Validation(DIRECTION)
        if (!updated.amount.isPositive) return AppError.Validation(AMOUNT_LOCKED)

        if (link.kind == InstallmentPostingKind.PREPAY) {
            val existing = txns.getById(updated.id)
            if (updated.isDeleted || existing == null || existing.amount != updated.amount) {
                return AppError.Validation(AMOUNT_LOCKED)
            }
            return null
        }
        return resyncPeriod(plan.id, link.periodNo, updated)
    }

    /** 按存活流水重算该期累计；本金/利息/费各自不得超过该期应还。 */
    private suspend fun resyncPeriod(planId: String, periodNo: Int, updated: Txn): AppError? {
        val period = repository.getPeriod(planId, periodNo) ?: return AppError.Validation(LINK_INVALID)
        val entries = txns.listByLedger(updated.ledgerId).associateBy { it.id }
        val links = repository.linksForPlan(planId).filter { it.periodNo == periodNo }
        fun paidOf(kind: InstallmentPostingKind): Money = Money.sum(
            links.filter { it.kind == kind }.mapNotNull { link ->
                val txn = if (link.txnId == updated.id) updated else entries[link.txnId]
                txn?.takeIf { !it.isDeleted }?.amount
            },
        )
        val paidFee = paidOf(InstallmentPostingKind.FEE)
        val paidInterest = paidOf(InstallmentPostingKind.INTEREST)
        val paidPrincipal = paidOf(InstallmentPostingKind.PRINCIPAL)
        if (paidFee > period.fee || paidInterest > period.interest || paidPrincipal > period.principal) {
            return AppError.Validation(OVER_PAID)
        }
        val paidTotal = paidFee + paidInterest + paidPrincipal
        val status = when {
            paidTotal >= period.total -> InstallmentPeriodStatus.PAID
            paidTotal.isZero -> InstallmentPeriodStatus.PENDING
            else -> InstallmentPeriodStatus.PARTIAL
        }
        val now = timeProvider.nowMillis()
        repository.savePeriods(
            listOf(
                period.copy(
                    status = status,
                    paidPrincipal = paidPrincipal,
                    paidInterest = paidInterest,
                    paidFee = paidFee,
                    paidTxnId = if (status == InstallmentPeriodStatus.PENDING) null else period.paidTxnId,
                    paidAt = if (status == InstallmentPeriodStatus.PENDING) null else period.paidAt,
                ),
            ),
        ).let { if (it is AppResult.Failure) return it.error }
        val plan = repository.getPlan(planId) ?: return AppError.Validation(LINK_INVALID)
        if (plan.deletedAt == null) {
            val all = repository.listPeriods(planId)
            val target = if (all.isNotEmpty() && all.all { it.isPaid }) {
                InstallmentPlanStatus.CLOSED
            } else {
                InstallmentPlanStatus.ACTIVE
            }
            if (plan.status != target) {
                repository.updatePlanStatus(planId, target, now)
                    .let { if (it is AppResult.Failure) return it.error }
            }
        }
        return null
    }
}
