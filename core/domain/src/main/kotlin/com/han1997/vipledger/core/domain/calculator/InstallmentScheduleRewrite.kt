package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.money.Money

/**
 * 未还期次的重算（P4）。
 *
 * 部分提前还款与利率变更都只重算「完全未还」的尾部期次：已还、部分已还的期次原样保留，
 * 期数与还款日不变（部分提前还款默认保持余期数）。本金与利息都由计息引擎重新摊销，
 * 因此「各期本金之和 = 计划本金」在重写后依然成立（计划本金与未还本金同步下调）。
 */
object InstallmentScheduleRewrite {

    /**
     * 重算从 [tailStartIndex] 起的尾部期次。
     *
     * @param newRateBp 尾部使用的新年化基点（利率变更用；提前还款传原利率）。
     * @param principalReduction 从尾部本金总额里扣减的金额（部分提前还款用，利率变更传 0）。
     */
    fun rewriteTail(
        plan: InstallmentPlan,
        periods: List<InstallmentPeriod>,
        tailStartIndex: Int,
        newRateBp: Int = plan.annualRateBp,
        principalReduction: Money = Money.ZERO,
    ): List<InstallmentPeriod> {
        require(newRateBp >= 0) { "年化利率不能为负" }
        require(!principalReduction.isNegative) { "提前还款金额不能为负" }
        val ordered = periods.sortedBy { it.periodNo }
        require(tailStartIndex in 0 until ordered.size) { "起始期次超出范围" }
        val tail = ordered.subList(tailStartIndex, ordered.size)
        require(tail.all { it.isUntouched }) { "只能重算完全未还的期次" }
        val tailPrincipal = Money.sum(tail.map { it.principal })
        val newTailPrincipal = tailPrincipal - principalReduction
        require(newTailPrincipal.isPositive) { "提前还款金额不能达到或超过未还本金" }

        val recomputed = if (plan.method == InstallmentMethod.CUSTOM) {
            reduceCustomTail(tail, principalReduction)
        } else {
            val schedule = InstallmentCalculator.schedule(
                plan.method,
                InstallmentScheduleInput(
                    principal = newTailPrincipal,
                    periods = tail.size,
                    firstDueDate = tail.first().dueDate,
                    annualRateBp = newRateBp,
                    upfrontFee = Money.ZERO,
                    perPeriodFee = plan.perPeriodFee,
                ),
            )
            schedule.mapIndexed { index, scheduled ->
                tail[index].copy(
                    principal = scheduled.principal,
                    interest = scheduled.interest,
                    fee = scheduled.fee,
                )
            }
        }
        return ordered.subList(0, tailStartIndex) + recomputed
    }

    /** 利率变更：从指定期次起按新利率重算，本金不变。 */
    fun changeRate(
        plan: InstallmentPlan,
        periods: List<InstallmentPeriod>,
        fromPeriodNo: Int,
        newRateBp: Int,
    ): List<InstallmentPeriod> {
        val ordered = periods.sortedBy { it.periodNo }
        val index = ordered.indexOfFirst { it.periodNo == fromPeriodNo }
        require(index >= 0) { "指定的生效期次不存在" }
        return rewriteTail(plan, ordered, index, newRateBp = newRateBp)
    }

    /** 自定义期不重新摊销：从最后一期往前扣减本金，利息保持用户录入值。 */
    private fun reduceCustomTail(
        tail: List<InstallmentPeriod>,
        reduction: Money,
    ): List<InstallmentPeriod> {
        if (reduction.isZero) return tail
        val result = tail.toMutableList()
        var remaining = reduction
        for (index in result.indices.reversed()) {
            if (remaining.isZero) break
            val period = result[index]
            val cut = if (period.principal < remaining) period.principal else remaining
            result[index] = period.copy(principal = period.principal - cut)
            remaining -= cut
        }
        require(remaining.isZero) { "提前还款金额超过未还本金" }
        return result
    }
}