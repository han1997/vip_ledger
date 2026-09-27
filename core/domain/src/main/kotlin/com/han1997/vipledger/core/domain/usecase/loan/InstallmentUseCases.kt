package com.han1997.vipledger.core.domain.usecase.loan

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** 分期计划 + 各期明细，详情页一次消费。 */
data class InstallmentPlanDetail(
    val plan: InstallmentPlan,
    val periods: List<InstallmentPeriod>,
) {
    val paidCount: Int get() = periods.count { it.status == InstallmentPeriodStatus.PAID }

    val remainingAmount: Money
        get() = Money.sum(
            periods.filter { it.status == InstallmentPeriodStatus.PENDING }.map { it.total },
        )

    val nextPeriod: InstallmentPeriod?
        get() = periods.filter { it.status == InstallmentPeriodStatus.PENDING }.minByOrNull { it.periodNo }
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
                    paidPeriods = periods.count { it.status == InstallmentPeriodStatus.PAID },
                    remainingAmount = Money.sum(
                        periods.filter { it.status == InstallmentPeriodStatus.PENDING }.map { it.total },
                    ),
                    nextDueDate = periods.filter { it.status == InstallmentPeriodStatus.PENDING }
                        .minOfOrNull { it.dueDate },
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

/** 还款入参：从哪个账户还哪一期。 */
data class RepayInstallmentPeriodInput(
    val planId: String,
    val periodNo: Int,
    val fromAccountId: String,
    /** 还款时间；为空用当前时间。 */
    val occurredAt: Long? = null,
    /** 生成的流水标题；为空用计划名。 */
    val title: String = "",
)

/**
 * 分期还款核销。
 *
 * 一次事务里完成四件事：写 `DEBT_REPAY` 流水（还款账户 → 负债账户）、标记该期为已还、
 * 重算两个账户余额、若所有期都已还则把计划置为 `CLOSED`。
 */
class RepayInstallmentPeriodUseCase(
    private val installmentRepository: InstallmentRepository,
    private val txnRepository: TxnRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(input: RepayInstallmentPeriodInput): AppResult<Txn> {
        val plan = installmentRepository.getPlan(input.planId)
            ?: return AppResult.failure(AppError.NotFound("分期计划不存在"))
        if (plan.deletedAt != null) return AppResult.failure(AppError.NotFound("分期计划已删除"))
        if (plan.status == InstallmentPlanStatus.CLOSED) {
            return AppResult.failure(AppError.Validation("计划已经结清"))
        }
        val period = installmentRepository.getPeriod(input.planId, input.periodNo)
            ?: return AppResult.failure(AppError.NotFound("该期不存在"))
        if (period.status == InstallmentPeriodStatus.PAID) {
            return AppResult.failure(AppError.Validation("该期已经还过"))
        }

        val accounts = accountRepository.listByLedger(plan.ledgerId, includeArchived = true)
        val categories = categoryRepository.listByLedger(plan.ledgerId, includeArchived = true)
        val fromAccount = accounts.firstOrNull { it.id == input.fromAccountId && it.deletedAt == null }
            ?: return AppResult.failure(AppError.Validation("还款账户不存在"))
        if (fromAccount.id == plan.accountId) {
            return AppResult.failure(AppError.Validation("还款账户不能是负债账户本身"))
        }

        val now = timeProvider.nowMillis()
        val txn = Txn(
            id = idGenerator.newId(),
            ledgerId = plan.ledgerId,
            type = TransactionType.DEBT_REPAY,
            amount = period.total,
            accountId = fromAccount.id,
            toAccountId = plan.accountId,
            categoryId = null,
            occurredAt = input.occurredAt ?: now,
            title = input.title.ifBlank { plan.name },
            source = TxnSource.MANUAL,
            draftStatus = DraftStatus.CONFIRMED,
            installmentPlanId = plan.id,
            installmentPeriodNo = period.periodNo,
            createdAt = now,
            updatedAt = now,
        )
        TxnValidator.validate(txn, accounts, categories)?.let { return AppResult.failure(it) }

        val allPeriods = installmentRepository.listPeriods(plan.id)
        val willBeLast = allPeriods.none { it.periodNo != period.periodNo && it.status == InstallmentPeriodStatus.PENDING }

        val result = transactionRunner.inTransaction {
            val inserted = txnRepository.insert(txn)
            if (!inserted.isSuccess) return@inTransaction inserted
            val marked = installmentRepository.markPeriodPaid(plan.id, period.periodNo, txn.id, now)
            if (!marked.isSuccess) return@inTransaction marked
            if (willBeLast) {
                val closed = installmentRepository.updatePlanStatus(plan.id, InstallmentPlanStatus.CLOSED, now)
                if (!closed.isSuccess) return@inTransaction closed
            }
            balanceSyncer.syncBalances(listOf(fromAccount.id, plan.accountId), now)
            AppResult.success(Unit)
        }
        return when (result) {
            is AppResult.Success -> AppResult.success(txn)
            is AppResult.Failure -> result
        }
    }
}
