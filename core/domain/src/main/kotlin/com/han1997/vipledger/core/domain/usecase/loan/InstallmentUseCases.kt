package com.han1997.vipledger.core.domain.usecase.loan

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** 分期计划 + 各期明细，详情页一次消费。 */
data class InstallmentPlanDetail(
    val plan: InstallmentPlan,
    val periods: List<InstallmentPeriod>,
) {
    val paidCount: Int get() = periods.count { it.isPaid }

    val partialCount: Int get() = periods.count { it.isPartiallyPaid }

    /** 未还总额（本金 + 利息 + 手续费）。 */
    val remainingAmount: Money get() = Money.sum(periods.map { it.remainingTotal })

    /** 未还本金，提前还款的上限。 */
    val remainingPrincipal: Money get() = Money.sum(periods.map { it.remainingPrincipal })

    val nextPeriod: InstallmentPeriod? get() = periods.filter { !it.isPaid }.minByOrNull { it.periodNo }

    val isClosed: Boolean get() = plan.status == InstallmentPlanStatus.CLOSED
}

/** 订阅账本下的分期计划（列表页用）。 */
class ObserveInstallmentPlansUseCase(private val repository: InstallmentRepository) {
    operator fun invoke(ledgerId: String): Flow<List<InstallmentPlan>> = repository.observePlans(ledgerId)
}

/** 列表页用的分期概览：计划 + 还款进度 + 下一期。 */
data class InstallmentSummary(
    val plan: InstallmentPlan,
    val totalPeriods: Int,
    val paidPeriods: Int,
    val remainingAmount: Money,
    val nextDueDate: LocalDate?,
) {
    val isClosed: Boolean get() = plan.status == InstallmentPlanStatus.CLOSED
}

/** 计划与各期的联动订阅：计划或任一期变化都会重新汇总。 */
class ObserveInstallmentSummariesUseCase(private val repository: InstallmentRepository) {

    operator fun invoke(ledgerId: String): Flow<List<InstallmentSummary>> =
        repository.observePlans(ledgerId).map { plans ->
            val periodsByPlan = repository.listPeriodsForLedger(ledgerId).groupBy { it.planId }
            plans.map { plan ->
                val periods = periodsByPlan[plan.id].orEmpty()
                InstallmentSummary(
                    plan = plan,
                    totalPeriods = periods.size,
                    paidPeriods = periods.count { it.isPaid },
                    remainingAmount = Money.sum(periods.map { it.remainingTotal }),
                    nextDueDate = periods.filter { !it.isPaid }.minOfOrNull { it.dueDate },
                )
            }
        }
}

/** 读取计划详情；计划已删除或不存在时返回 null。 */
class GetInstallmentPlanDetailUseCase(private val repository: InstallmentRepository) {
    suspend operator fun invoke(planId: String): InstallmentPlanDetail? {
        val plan = repository.getPlan(planId)?.takeIf { it.deletedAt == null } ?: return null
        return InstallmentPlanDetail(plan = plan, periods = repository.listPeriods(planId))
    }
}

/** 删除（软删）分期计划；已经生成的还款流水保留。 */
class DeleteInstallmentPlanUseCase(
    private val repository: InstallmentRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(planId: String): AppResult<Unit> {
        val plan = repository.getPlan(planId) ?: return AppResult.failure(AppError.NotFound("分期计划不存在"))
        if (plan.deletedAt != null) return AppResult.failure(AppError.NotFound("分期计划已删除"))
        return repository.softDeletePlan(planId, timeProvider.nowMillis())
    }
}