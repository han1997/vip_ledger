package com.han1997.vipledger.core.domain.usecase.recurring

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.RecurrenceCalculator
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.RecurringRuleRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate

/** 新建 / 编辑周期规则的入参（`docs/DATA-MODEL.md` 3.6）。 */
data class RecurringRuleInput(
    val ledgerId: String,
    val ruleId: String? = null,
    val name: String,
    val txnType: TransactionType,
    val amount: Money,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val tagIds: List<String> = emptyList(),
    val title: String = "",
    val remark: String = "",
    val freq: RecurrenceFrequency,
    val interval: Int = 1,
    val byWeekday: Int? = null,
    val byMonthDay: Int? = null,
    val byMonth: Int? = null,
    val startDate: LocalDate,
    val endType: RecurrenceEndType = RecurrenceEndType.NEVER,
    val endDate: LocalDate? = null,
    val maxOccurrences: Int? = null,
    val generateMode: GenerateMode = GenerateMode.DRAFT,
    val missedPolicy: MissedPolicy = MissedPolicy.SKIP,
    val enabled: Boolean = true,
)

/**
 * 保存周期规则：校验账户 / 分类 / 结束条件，并重新计算 `next_occurrence_at` 游标。
 *
 * 编辑时保留 `generatedCount`（`COUNT` 型结束条件的「已经生成过几期」语义不变），
 * 只把游标指向「第 generatedCount 期」，避免编辑一次就把过去的账重新生成一遍。
 */
@OptIn(ExperimentalTime::class)
class SaveRecurringRuleUseCase(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(input: RecurringRuleInput): AppResult<RecurringRule> {
        validateInput(input)?.let { return AppResult.failure(it) }

        val existing = input.ruleId?.let { recurringRuleRepository.getById(it) }
        if (input.ruleId != null && (existing == null || existing.deletedAt != null)) {
            return AppResult.failure(AppError.NotFound("周期规则不存在"))
        }
        if (existing != null && existing.ledgerId != input.ledgerId) {
            return AppResult.failure(AppError.Validation("周期规则不属于当前账本"))
        }

        val now = timeProvider.nowMillis()
        val draft = RecurringRule(
            id = existing?.id ?: idGenerator.newId(),
            ledgerId = input.ledgerId,
            name = input.name.trim(),
            txnType = input.txnType,
            amount = input.amount,
            accountId = input.accountId,
            toAccountId = input.toAccountId,
            categoryId = input.categoryId,
            tagIds = input.tagIds.distinct(),
            title = input.title.trim(),
            remark = input.remark.trim(),
            freq = input.freq,
            interval = input.interval.coerceAtLeast(1),
            byWeekday = input.byWeekday,
            byMonthDay = input.byMonthDay,
            byMonth = input.byMonth,
            startAt = input.startDate.toDateColumnEpochMillis(),
            endType = input.endType,
            endDate = input.endDate?.toDateColumnEpochMillis(),
            maxOccurrences = input.maxOccurrences,
            generatedCount = existing?.generatedCount ?: 0,
            nextOccurrenceAt = null,
            lastGeneratedAt = existing?.lastGeneratedAt,
            generateMode = input.generateMode,
            missedPolicy = input.missedPolicy,
            enabled = input.enabled,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val rule = draft.copy(
            nextOccurrenceAt = RecurrenceCalculator.nextOccurrenceDate(draft, draft.generatedCount)
                ?.toDateColumnEpochMillis(),
        )
        return recurringRuleRepository.upsert(rule).map { rule }
    }

    private suspend fun validateInput(input: RecurringRuleInput): AppError? {
        if (input.name.isBlank()) return AppError.Validation("请填写规则名称")
        if (input.txnType == TransactionType.ADJUST) {
            return AppError.Validation("周期规则不支持余额调整")
        }
        if (!input.amount.isPositive) return AppError.Validation("金额必须大于 0")
        if (input.interval < 1) return AppError.Validation("间隔必须是正整数")

        if (input.byWeekday != null && input.byWeekday !in 0..6) {
            return AppError.Validation("星期需在 0–6 之间")
        }
        if (input.byMonthDay != null && input.byMonthDay !in 1..31) {
            return AppError.Validation("日期需在 1–31 之间")
        }
        if (input.byMonth != null && input.byMonth !in 1..12) {
            return AppError.Validation("月份需在 1–12 之间")
        }
        when (input.endType) {
            RecurrenceEndType.NEVER -> Unit
            RecurrenceEndType.UNTIL_DATE -> {
                val end = input.endDate ?: return AppError.Validation("请选择结束日期")
                if (end < input.startDate) return AppError.Validation("结束日期不能早于开始日期")
            }
            RecurrenceEndType.COUNT -> {
                val count = input.maxOccurrences ?: return AppError.Validation("请填写生成次数")
                if (count < 1) return AppError.Validation("生成次数必须大于 0")
            }
        }

        val accounts = accountRepository.listByLedger(input.ledgerId, includeArchived = true)
        val account = accounts.firstOrNull { it.id == input.accountId && it.deletedAt == null }
            ?: return AppError.Validation("账户不存在")
        if (account.ledgerId != input.ledgerId) return AppError.Validation("账户不属于当前账本")
        if (input.txnType.requiresTargetAccount) {
            val targetId = input.toAccountId ?: return AppError.Validation("请选择目标账户")
            if (targetId == input.accountId) return AppError.Validation("转出与转入账户不能相同")
            val target = accounts.firstOrNull { it.id == targetId && it.deletedAt == null }
                ?: return AppError.Validation("目标账户不存在")
            if (target.ledgerId != input.ledgerId) return AppError.Validation("目标账户不属于当前账本")
            if (input.txnType == TransactionType.DEBT_REPAY && !target.type.isDebt) {
                return AppError.Validation("还款的目标账户必须是负债账户")
            }
        } else if (input.toAccountId != null) {
            return AppError.Validation("该类型不需要目标账户")
        }

        val categoryId = input.categoryId ?: return null
        val categories = categoryRepository.listByLedger(input.ledgerId, includeArchived = true)
        val category = categories.firstOrNull { it.id == categoryId && it.deletedAt == null }
            ?: return AppError.Validation("分类不存在")
        if (category.ledgerId != input.ledgerId) return AppError.Validation("分类不属于当前账本")
        if (category.kind != TxnValidator.expectedKind(input.txnType)) {
            return AppError.Validation("分类的收支方向与类型不一致")
        }
        return null
    }
}
