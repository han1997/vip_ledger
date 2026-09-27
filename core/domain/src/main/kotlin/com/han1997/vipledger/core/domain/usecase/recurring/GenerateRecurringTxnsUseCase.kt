package com.han1997.vipledger.core.domain.usecase.recurring

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.RecurrenceCalculator
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.RecurringRuleRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.TxnSource
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate

/** 一次周期生成的结果：生成了几笔、处理了几条规则、跳过了几条（账户/分类已失效等）。 */
data class RecurringGenerationReport(
    val generated: Int = 0,
    val rulesProcessed: Int = 0,
    val rulesSkipped: Int = 0,
)

/**
 * 周期流水生成引擎（M4 核心）。
 *
 * 对账本下所有启用的规则推进游标：
 * - `DRAFT` 模式生成待确认草稿（不进统计、不进余额），`AUTO` 模式直接入账；
 * - `SKIP` 只补最近一期，`CATCH_UP` 补齐全部错过的周期（上限 [RecurrenceCalculator.MAX_CATCH_UP]）；
 * - 无论哪种模式，游标都会推进到「已处理完的期数」，因此重复调用是幂等的；
 * - 流水写入与规则游标更新在同一事务内完成。
 */
@OptIn(ExperimentalTime::class)
class GenerateRecurringTxnsUseCase(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val txnRepository: TxnRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(
        ledgerId: String,
        today: LocalDate = timeProvider.nowMillis().toLocalDate(timeProvider.zone),
    ): AppResult<RecurringGenerationReport> {
        val rules = recurringRuleRepository.listEnabled(ledgerId)
        if (rules.isEmpty()) return AppResult.success(RecurringGenerationReport())

        val accounts = accountRepository.listByLedger(ledgerId, includeArchived = true)
        val categories = categoryRepository.listByLedger(ledgerId, includeArchived = true)
        val now = timeProvider.nowMillis()

        return transactionRunner.inTransaction {
            var generated = 0
            var skipped = 0
            var processed = 0
            val touchedAccounts = mutableSetOf<String>()

            rules.forEach { rule ->
                val due = RecurrenceCalculator.dueOccurrences(rule, today)
                if (due.isEmpty()) return@forEach
                val selected = when (rule.missedPolicy) {
                    MissedPolicy.SKIP -> listOf(due.last())
                    MissedPolicy.CATCH_UP -> due
                }
                var created = 0
                selected.forEach { date ->
                    val txn = rule.toTxn(date, now, idGenerator.newId())
                    TxnValidator.validate(txn, accounts, categories)?.let { return@forEach }
                    if (txnRepository.insert(txn).isSuccess) {
                        created++
                        generated++
                        if (txn.draftStatus == DraftStatus.CONFIRMED) {
                            touchedAccounts += listOfNotNull(txn.accountId, txn.toAccountId)
                        }
                    }
                }
                if (created == 0) {
                    skipped++
                    return@forEach
                }
                // 游标按「看到的期数」推进（SKIP 也要跳过被忽略的旧期），保证幂等。
                processed++
                val nextRule = rule.copy(generatedCount = rule.generatedCount + due.size)
                recurringRuleRepository.markGenerated(
                    ruleId = rule.id,
                    generatedCount = nextRule.generatedCount,
                    nextOccurrenceAt = RecurrenceCalculator
                        .nextOccurrenceDate(nextRule, nextRule.generatedCount)
                        ?.toDateColumnEpochMillis(),
                    lastGeneratedAt = now,
                    updatedAt = now,
                )
            }

            if (touchedAccounts.isNotEmpty()) {
                balanceSyncer.syncBalances(touchedAccounts, now)
            }
            AppResult.success(
                RecurringGenerationReport(
                    generated = generated,
                    rulesProcessed = processed,
                    rulesSkipped = skipped,
                ),
            )
        }
    }

    private fun com.han1997.vipledger.core.domain.model.RecurringRule.toTxn(
        date: LocalDate,
        now: Long,
        id: String,
    ): Txn = Txn(
        id = id,
        ledgerId = ledgerId,
        type = txnType,
        amount = amount,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        occurredAt = date.startOfDayEpochMillis(timeProvider.zone),
        title = title.ifBlank { name },
        remark = remark,
        source = TxnSource.RECURRING,
        draftStatus = if (generateMode == GenerateMode.AUTO) DraftStatus.CONFIRMED else DraftStatus.PENDING,
        excludeFromStats = false,
        recurringRuleId = this.id,
        tagIds = tagIds,
        createdAt = now,
        updatedAt = now,
    )
}
