package com.han1997.vipledger.core.domain.usecase.loan

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.InstallmentScheduleRewrite
import com.han1997.vipledger.core.domain.model.InstallmentAllocation
import com.han1997.vipledger.core.domain.model.InstallmentLink
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.support.checkedWrite
import com.han1997.vipledger.core.domain.support.orRollback
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.InstallmentPostingKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money

// ---------------------------------------------------------------------------
// 共用：分账与落账
// ---------------------------------------------------------------------------

/**
 * 还款分账：按「费 → 息 → 本」的顺序核销，[amount] 不得超过该期未还总额。
 */
internal fun allocateRepayment(period: InstallmentPeriod, amount: Money): List<InstallmentAllocation> {
    var remaining = amount
    val result = ArrayList<InstallmentAllocation>(3)
    fun take(outstanding: Money, kind: InstallmentPostingKind) {
        if (remaining.isZero || !outstanding.isPositive) return
        val part = if (outstanding < remaining) outstanding else remaining
        result += InstallmentAllocation(period.periodNo, kind, part)
        remaining -= part
    }
    take(period.fee - period.paidFee, InstallmentPostingKind.FEE)
    take(period.interest - period.paidInterest, InstallmentPostingKind.INTEREST)
    take(period.principal - period.paidPrincipal, InstallmentPostingKind.PRINCIPAL)
    return result
}

/** 把分账写成流水并建立关联。必须在事务内调用。 */
internal suspend fun postRepayment(
    plan: InstallmentPlan,
    fromAccountId: String,
    allocations: List<InstallmentAllocation>,
    kindOverride: InstallmentPostingKind? = null,
    occurredAt: Long,
    title: String,
    now: Long,
    create: CreateTxnUseCase,
    repository: InstallmentRepository,
): List<Txn> {
    val result = ArrayList<Txn>(allocations.size)
    allocations.forEach { allocation ->
        val kind = kindOverride ?: allocation.kind
        val isPrincipal = kind == InstallmentPostingKind.PRINCIPAL || kind == InstallmentPostingKind.PREPAY
        val draft = TxnDraft(
            ledgerId = plan.ledgerId,
            type = if (isPrincipal) TransactionType.TRANSFER else TransactionType.EXPENSE,
            amount = allocation.amount,
            accountId = fromAccountId,
            toAccountId = if (isPrincipal) plan.accountId else null,
            occurredAt = occurredAt,
            title = title.ifBlank { plan.name },
            installmentPlanId = plan.id,
            installmentPeriodNo = allocation.periodNo,
        )
        val txn = create(draft).orRollback()
        repository.link(InstallmentLink(txn.id, plan.id, allocation.periodNo, kind, now)).orRollback()
        result += txn
    }
    return result
}

// ---------------------------------------------------------------------------
// 逐期核销（支持部分还款）
// ---------------------------------------------------------------------------

/** 还款入参：从哪个账户还哪一期；[amount] 为空表示还清该期。 */
data class RepayInstallmentPeriodInput(
    val planId: String,
    val periodNo: Int,
    val fromAccountId: String,
    val amount: Money? = null,
    val occurredAt: Long? = null,
    val title: String = "",
)

/**
 * 逐期核销。一次事务里按「费 → 息 → 本」分账写成 1–3 条流水
 * （本金 `TRANSFER` 流入负债账户，利息/手续费 `EXPENSE` 计入支出），
 * 回填该期累计与状态（还清 `PAID`，否则 `PARTIAL`），全部还清时把计划置为 `CLOSED`。
 */
class RepayInstallmentPeriodUseCase(
    private val installmentRepository: InstallmentRepository,
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val createTxn: CreateTxnUseCase,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(input: RepayInstallmentPeriodInput): AppResult<List<Txn>> {
        val plan = installmentRepository.getPlan(input.planId)
            ?: return AppResult.failure(AppError.NotFound("分期计划不存在"))
        if (plan.deletedAt != null) return AppResult.failure(AppError.NotFound("分期计划已删除"))
        if (plan.status == InstallmentPlanStatus.CLOSED) {
            return AppResult.failure(AppError.Validation("计划已经结清"))
        }
        val period = installmentRepository.getPeriod(input.planId, input.periodNo)
            ?: return AppResult.failure(AppError.NotFound("该期不存在"))
        if (period.isPaid) return AppResult.failure(AppError.Validation("该期已经还过"))

        val amount = input.amount ?: period.remainingTotal
        if (!amount.isPositive) return AppResult.failure(AppError.Validation("还款金额必须大于 0"))
        if (amount > period.remainingTotal) {
            return AppResult.failure(AppError.Validation("还款金额不能超过本期未还金额"))
        }
        val fromAccount = accountRepository.getById(input.fromAccountId)
            ?: return AppResult.failure(AppError.Validation("还款账户不存在"))
        if (fromAccount.id == plan.accountId) {
            return AppResult.failure(AppError.Validation("还款账户不能是负债账户本身"))
        }

        val allocations = allocateRepayment(period, amount)
        val now = timeProvider.nowMillis()
        val occurredAt = input.occurredAt ?: now
        val title = input.title.ifBlank { plan.name }

        return checkedWrite {
            transactionRunner.inTransaction {
                val txns = postRepayment(
                    plan = plan,
                    fromAccountId = fromAccount.id,
                    allocations = allocations,
                    occurredAt = occurredAt,
                    title = title,
                    now = now,
                    create = createTxn,
                    repository = installmentRepository,
                )
                val paidFee = period.paidFee + allocations.sumOf(InstallmentPostingKind.FEE)
                val paidInterest = period.paidInterest + allocations.sumOf(InstallmentPostingKind.INTEREST)
                val paidPrincipal = period.paidPrincipal + allocations.sumOf(InstallmentPostingKind.PRINCIPAL)
                val paidTotal = paidFee + paidInterest + paidPrincipal
                val paid = InstallmentPeriod(
                    id = period.id,
                    planId = period.planId,
                    periodNo = period.periodNo,
                    dueDate = period.dueDate,
                    principal = period.principal,
                    interest = period.interest,
                    fee = period.fee,
                    status = if (paidTotal >= period.total) InstallmentPeriodStatus.PAID else InstallmentPeriodStatus.PARTIAL,
                    paidPrincipal = paidPrincipal,
                    paidInterest = paidInterest,
                    paidFee = paidFee,
                    paidTxnId = txns.firstOrNull { it.type == TransactionType.TRANSFER }?.id ?: txns.first().id,
                    paidAt = now,
                )
                installmentRepository.savePeriods(listOf(paid)).orRollback()
                val allPeriods = installmentRepository.listPeriods(plan.id)
                if (allPeriods.isNotEmpty() && allPeriods.all { it.isPaid }) {
                    installmentRepository.updatePlanStatus(plan.id, InstallmentPlanStatus.CLOSED, now).orRollback()
                }
                txns
            }
        }
    }

    private fun List<InstallmentAllocation>.sumOf(kind: InstallmentPostingKind): Money =
        Money.sum(filter { it.kind == kind }.map { it.amount })
}

// ---------------------------------------------------------------------------
// 提前结清
// ---------------------------------------------------------------------------

data class SettleInstallmentPlanInput(
    val planId: String,
    val fromAccountId: String,
    val occurredAt: Long? = null,
    val title: String = "",
)

/** 提前结清：把全部未还金额分账还清并关闭计划。 */
class SettleInstallmentPlanUseCase(
    private val installmentRepository: InstallmentRepository,
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val createTxn: CreateTxnUseCase,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(input: SettleInstallmentPlanInput): AppResult<List<Txn>> {
        val plan = installmentRepository.getPlan(input.planId)
            ?: return AppResult.failure(AppError.NotFound("分期计划不存在"))
        if (plan.deletedAt != null) return AppResult.failure(AppError.NotFound("分期计划已删除"))
        if (plan.status == InstallmentPlanStatus.CLOSED) {
            return AppResult.failure(AppError.Validation("计划已经结清"))
        }
        val periods = installmentRepository.listPeriods(plan.id)
        val outstanding = periods.filter { !it.isPaid }
        if (outstanding.isEmpty()) return AppResult.failure(AppError.Validation("计划已经没有未还金额"))
        val fromAccount = accountRepository.getById(input.fromAccountId)
            ?: return AppResult.failure(AppError.Validation("还款账户不存在"))
        if (fromAccount.id == plan.accountId) {
            return AppResult.failure(AppError.Validation("还款账户不能是负债账户本身"))
        }

        val allocations = outstanding.flatMap { allocateRepayment(it, it.remainingTotal) }
        val now = timeProvider.nowMillis()
        val occurredAt = input.occurredAt ?: now
        val title = input.title.ifBlank { plan.name }

        return checkedWrite {
            transactionRunner.inTransaction {
                val txns = postRepayment(
                    plan = plan,
                    fromAccountId = fromAccount.id,
                    allocations = allocations,
                    occurredAt = occurredAt,
                    title = title,
                    now = now,
                    create = createTxn,
                repository = installmentRepository,
                )
                val settled = periods.map { period ->
                    if (period.isPaid) {
                        period
                    } else {
                        period.copy(
                            status = InstallmentPeriodStatus.PAID,
                            paidPrincipal = period.principal,
                            paidInterest = period.interest,
                            paidFee = period.fee,
                            paidTxnId = period.paidTxnId ?: txns.first().id,
                            paidAt = period.paidAt ?: now,
                        )
                    }
                }
                installmentRepository.savePeriods(settled).orRollback()
                installmentRepository.updatePlanStatus(plan.id, InstallmentPlanStatus.CLOSED, now).orRollback()
                txns
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 部分提前还款
// ---------------------------------------------------------------------------

data class PrepayInstallmentPlanInput(
    val planId: String,
    val fromAccountId: String,
    val amount: Money,
    val occurredAt: Long? = null,
    val title: String = "",
)

/**
 * 部分提前还款：默认保持余期数，等额下调未还期次的本金并按原计息方式重算，已还不改。
 * 计划本金与未还本金同步下调，因此「各期本金之和 = 计划本金」不被破坏。
 */
class PrepayInstallmentPlanUseCase(
    private val installmentRepository: InstallmentRepository,
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val createTxn: CreateTxnUseCase,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(input: PrepayInstallmentPlanInput): AppResult<Txn> {
        val plan = installmentRepository.getPlan(input.planId)
            ?: return AppResult.failure(AppError.NotFound("分期计划不存在"))
        if (plan.deletedAt != null) return AppResult.failure(AppError.NotFound("分期计划已删除"))
        if (plan.status == InstallmentPlanStatus.CLOSED) {
            return AppResult.failure(AppError.Validation("计划已经结清"))
        }
        if (!input.amount.isPositive) return AppResult.failure(AppError.Validation("提前还款金额必须大于 0"))
        val periods = installmentRepository.listPeriods(plan.id).sortedBy { it.periodNo }
        if (periods.any { it.isPartiallyPaid }) {
            return AppResult.failure(AppError.Validation("存在部分还款的期次，暂不支持提前还款"))
        }
        val untouched = periods.filter { it.isUntouched }
        if (untouched.isEmpty()) return AppResult.failure(AppError.Validation("计划没有可提前偿还的本金"))
        val outstandingPrincipal = Money.sum(untouched.map { it.principal })
        if (input.amount >= outstandingPrincipal) {
            return AppResult.failure(AppError.Validation("提前还款金额不能达到或超过未还本金，请使用提前结清"))
        }
        val fromAccount = accountRepository.getById(input.fromAccountId)
            ?: return AppResult.failure(AppError.Validation("还款账户不存在"))
        if (fromAccount.id == plan.accountId) {
            return AppResult.failure(AppError.Validation("还款账户不能是负债账户本身"))
        }
        val tailStartIndex = periods.indexOfFirst { it.isUntouched }
        val rewritten = try {
            InstallmentScheduleRewrite.rewriteTail(
                plan = plan,
                periods = periods,
                tailStartIndex = tailStartIndex,
                principalReduction = input.amount,
            )
        } catch (error: IllegalArgumentException) {
            return AppResult.failure(AppError.Validation(error.message, error))
        }
        val now = timeProvider.nowMillis()
        val occurredAt = input.occurredAt ?: now
        val title = input.title.ifBlank { plan.name }
        val allocation = listOf(
            InstallmentAllocation(untouched.first().periodNo, InstallmentPostingKind.PREPAY, input.amount),
        )

        return checkedWrite {
            transactionRunner.inTransaction {
                val txns = postRepayment(
                    plan = plan,
                    fromAccountId = fromAccount.id,
                    allocations = allocation,
                    occurredAt = occurredAt,
                    title = title,
                    now = now,
                    create = createTxn,
                repository = installmentRepository,
                )
                installmentRepository.savePeriods(rewritten).orRollback()
                installmentRepository.savePlan(
                    plan.copy(principal = plan.principal - input.amount, updatedAt = now),
                ).orRollback()
                txns.single()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 利率变更
// ---------------------------------------------------------------------------

data class ChangeInstallmentRateInput(
    val planId: String,
    val fromPeriodNo: Int,
    val annualRateBp: Int,
)

/** 利率变更：从指定未还期起按新利率重算利息，已还期次的历史金额保持不变。 */
class ChangeInstallmentRateUseCase(
    private val installmentRepository: InstallmentRepository,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(input: ChangeInstallmentRateInput): AppResult<Unit> {
        if (input.annualRateBp < 0) return AppResult.failure(AppError.Validation("年化利率必须是非负数"))
        val plan = installmentRepository.getPlan(input.planId)
            ?: return AppResult.failure(AppError.NotFound("分期计划不存在"))
        if (plan.deletedAt != null) return AppResult.failure(AppError.NotFound("分期计划已删除"))
        if (plan.status == InstallmentPlanStatus.CLOSED) {
            return AppResult.failure(AppError.Validation("计划已经结清"))
        }
        val periods = installmentRepository.listPeriods(plan.id)
        val target = periods.firstOrNull { it.periodNo == input.fromPeriodNo }
            ?: return AppResult.failure(AppError.Validation("指定的生效期次不存在"))
        if (!target.isUntouched) return AppResult.failure(AppError.Validation("只能从未还款的期次开始变更利率"))
        val rewritten = try {
            InstallmentScheduleRewrite.changeRate(plan, periods, input.fromPeriodNo, input.annualRateBp)
        } catch (error: IllegalArgumentException) {
            return AppResult.failure(AppError.Validation(error.message, error))
        }
        val now = timeProvider.nowMillis()
        return checkedWrite {
            transactionRunner.inTransaction {
                installmentRepository.savePeriods(rewritten).orRollback()
                installmentRepository.savePlan(
                    plan.copy(annualRateBp = input.annualRateBp, updatedAt = now),
                ).orRollback()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 旧口径差异预览与显式修正
// ---------------------------------------------------------------------------

/** 一期旧口径还款：历史单笔 `DEBT_REPAY` 金额 vs 新分账本金/利息/费。 */
data class LegacyInstallmentRepayment(
    val period: InstallmentPeriod,
    val legacyTxn: Txn,
    val allocations: List<InstallmentAllocation>,
) {
    val legacyAmount: Money get() = legacyTxn.amount

    val splitAmount: Money get() = period.total
}

/** 找出「旧口径」已还期次：已还但没有分账关联。只读，不写历史。 */
class PreviewLegacyInstallmentRepaymentsUseCase(
    private val repository: InstallmentRepository,
    private val txns: TxnRepository,
) {

    suspend operator fun invoke(planId: String): List<LegacyInstallmentRepayment> {
        val plan = repository.getPlan(planId)?.takeIf { it.deletedAt == null } ?: return emptyList()
        val links = repository.linksForPlan(plan.id)
        val periods = repository.listPeriods(plan.id)
        val entries = txns.listByLedger(plan.ledgerId).associateBy { it.id }
        return periods
            .filter { it.isPaid && links.none { link -> link.periodNo == it.periodNo } }
            .mapNotNull { period ->
                val legacy = period.paidTxnId?.let { entries[it] } ?: return@mapNotNull null
                if (legacy.deletedAt != null) return@mapNotNull null
                LegacyInstallmentRepayment(period, legacy, allocateRepayment(period, period.total))
            }
    }
}

/**
 * 用户显式修正旧口径：在同一事务里把历史单笔还款替换为分账流水并回填累计。
 * 原流水做软删除保留在回收站，绝不静默改写。
 */
class CorrectLegacyInstallmentRepaymentUseCase(
    private val repository: InstallmentRepository,
    private val txns: TxnRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(planId: String, periods: List<Int>? = null): AppResult<Int> {
        val plan = repository.getPlan(planId)?.takeIf { it.deletedAt == null }
            ?: return AppResult.failure(AppError.NotFound("分期计划不存在"))
        val links = repository.linksForPlan(plan.id)
        val all = repository.listPeriods(plan.id)
        val entries = txns.listByLedger(plan.ledgerId).associateBy { it.id }
        val targets = all
            .filter { periods == null || it.periodNo in periods }
            .filter { it.isPaid && links.none { link -> link.periodNo == it.periodNo } }
            .mapNotNull { period ->
                val legacy = period.paidTxnId?.let { entries[it] } ?: return@mapNotNull null
                if (legacy.deletedAt != null) return@mapNotNull null
                period to legacy
            }
        if (targets.isEmpty()) return AppResult.failure(AppError.Validation("没有需要修正的旧还款"))

        val now = timeProvider.nowMillis()
        return checkedWrite {
            transactionRunner.inTransaction {
                var count = 0
                targets.forEach { (period, legacy) ->
                    val allocations = allocateRepayment(period, period.total)
                    allocations.forEach { allocation ->
                        val isPrincipal = allocation.kind == InstallmentPostingKind.PRINCIPAL
                        val newId = idGenerator.newId()
                        txns.insert(
                            Txn(
                                id = newId,
                                ledgerId = plan.ledgerId,
                                type = if (isPrincipal) TransactionType.TRANSFER else TransactionType.EXPENSE,
                                amount = allocation.amount,
                                accountId = legacy.accountId,
                                toAccountId = if (isPrincipal) plan.accountId else null,
                                categoryId = null,
                                occurredAt = legacy.occurredAt,
                                title = legacy.title,
                                source = legacy.source,
                                draftStatus = legacy.draftStatus,
                                installmentPlanId = plan.id,
                                installmentPeriodNo = period.periodNo,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ).orRollback()
                        repository.link(
                            InstallmentLink(newId, plan.id, period.periodNo, allocation.kind, now),
                        ).orRollback()
                    }
                    txns.softDelete(legacy.id, now).orRollback()
                    repository.savePeriods(
                        listOf(
                            period.copy(
                                paidPrincipal = period.principal,
                                paidInterest = period.interest,
                                paidFee = period.fee,
                                paidAt = period.paidAt ?: now,
                            ),
                        ),
                    ).orRollback()
                    count++
                }
                balanceSyncer.syncBalances(listOfNotNull(plan.accountId), now)
                count
            }
        }
    }
}
