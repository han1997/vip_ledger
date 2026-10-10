package com.han1997.vipledger.core.domain.usecase.loan

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.InstallmentAmounts
import com.han1997.vipledger.core.domain.calculator.InstallmentCalculator
import com.han1997.vipledger.core.domain.calculator.InstallmentScheduleInput
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.support.checkedWrite
import com.han1997.vipledger.core.domain.support.orRollback
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentOrigin
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate

/**
 * 新建分期计划：算好每一期后与计划一起落库。
 *
 * [origin] 为 [InstallmentOrigin.DISBURSEMENT]（新放款）时，同一事务内在负债账户与放款到账账户之间
 * 发一笔本金 `TRANSFER`，避免「已有负债」重复计本金。
 */
data class SaveInstallmentPlanInput(
    val ledgerId: String,
    val accountId: String,
    val name: String,
    val method: InstallmentMethod,
    val principal: Money,
    val periods: Int,
    val firstDueDate: LocalDate,
    val annualRateBp: Int = 0,
    val dueDay: Int? = null,
    val upfrontFee: Money = Money.ZERO,
    val perPeriodFee: Money = Money.ZERO,
    val remark: String? = null,
    val origin: InstallmentOrigin = InstallmentOrigin.EXISTING,
    val disbursementAccountId: String? = null,
    /** [InstallmentMethod.CUSTOM] 旧口径：每期还款总额。 */
    val customPeriodTotals: List<Money>? = null,
    /** [InstallmentMethod.CUSTOM] 新口径：逐期本金/利息/费，优先于 [customPeriodTotals]。 */
    val customAmounts: List<InstallmentAmounts>? = null,
)

class SaveInstallmentPlanUseCase(
    private val installmentRepository: InstallmentRepository,
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val createTxn: CreateTxnUseCase,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(input: SaveInstallmentPlanInput): AppResult<InstallmentPlan> {
        if (input.name.isBlank()) return AppResult.failure(AppError.Validation("请填写计划名称"))
        val account = accountRepository.getById(input.accountId)
            ?: return AppResult.failure(AppError.Validation("账户不存在"))
        if (account.ledgerId != input.ledgerId) {
            return AppResult.failure(AppError.Validation("账户不属于当前账本"))
        }
        if (input.dueDay != null && input.dueDay !in 1..28) {
            return AppResult.failure(AppError.Validation("还款日需在 1–28 之间"))
        }
        val disbursementAccount = if (input.origin == InstallmentOrigin.DISBURSEMENT) {
            val target = input.disbursementAccountId?.let { accountRepository.getById(it) }
                ?: return AppResult.failure(AppError.Validation("请选择放款到账账户"))
            if (target.ledgerId != input.ledgerId || !target.isUsable || target.isDebt) {
                return AppResult.failure(AppError.Validation("放款到账账户需是可用的非负债账户"))
            }
            if (target.id == input.accountId) {
                return AppResult.failure(AppError.Validation("放款到账账户不能是负债账户本身"))
            }
            target
        } else {
            null
        }

        val scheduleInput = InstallmentScheduleInput(
            principal = input.principal,
            periods = input.periods,
            firstDueDate = input.firstDueDate,
            annualRateBp = input.annualRateBp,
            dueDay = input.dueDay,
            upfrontFee = input.upfrontFee,
            perPeriodFee = input.perPeriodFee,
        )
        val schedule = try {
            when {
                input.method == InstallmentMethod.CUSTOM && input.customAmounts != null ->
                    InstallmentCalculator.scheduleCustomAmounts(scheduleInput, input.customAmounts)

                input.method == InstallmentMethod.CUSTOM ->
                    InstallmentCalculator.scheduleCustom(scheduleInput, input.customPeriodTotals ?: emptyList())

                else -> InstallmentCalculator.schedule(input.method, scheduleInput)
            }
        } catch (error: IllegalArgumentException) {
            return AppResult.failure(AppError.Validation(error.message, error))
        }

        val now = timeProvider.nowMillis()
        val plan = InstallmentPlan(
            id = idGenerator.newId(),
            ledgerId = input.ledgerId,
            accountId = input.accountId,
            name = input.name,
            method = input.method,
            principal = input.principal,
            periods = input.periods,
            annualRateBp = input.annualRateBp,
            upfrontFee = input.upfrontFee,
            perPeriodFee = input.perPeriodFee,
            firstDueDate = input.firstDueDate,
            dueDay = input.dueDay,
            origin = input.origin,
            disbursementAccountId = disbursementAccount?.id,
            remark = input.remark,
            createdAt = now,
            updatedAt = now,
        )
        val periods = schedule.map { scheduled ->
            InstallmentPeriod(
                id = idGenerator.newId(),
                planId = plan.id,
                periodNo = scheduled.periodNo,
                dueDate = scheduled.dueDate,
                principal = scheduled.principal,
                interest = scheduled.interest,
                fee = scheduled.fee,
            )
        }
        val stored = if (disbursementAccount != null) plan.copy(disbursedAt = now) else plan
        return checkedWrite {
            transactionRunner.inTransaction {
                installmentRepository.savePlanWithPeriods(stored, periods).orRollback()
                if (disbursementAccount != null) {
                    createTxn(
                        TxnDraft(
                            ledgerId = stored.ledgerId,
                            type = TransactionType.TRANSFER,
                            amount = stored.principal,
                            accountId = stored.accountId,
                            toAccountId = disbursementAccount.id,
                            occurredAt = now,
                            title = stored.name,
                        ),
                    ).orRollback()
                }
            }
        }.map { stored }
    }
}
