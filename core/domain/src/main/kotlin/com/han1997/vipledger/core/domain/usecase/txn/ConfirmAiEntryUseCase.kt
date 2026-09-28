package com.han1997.vipledger.core.domain.usecase.txn

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isFailure
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.ml.MlModelTrainer
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.repository.MerchantRuleRepository
import com.han1997.vipledger.core.domain.repository.MlSampleRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.MlSampleSource
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField

/**
 * 学习规则（`RuleSource.LEARNED`）的 `priority`。
 *
 * `merchant_rule` 按 `priority` 升序尝试（越小越优先，见 [com.han1997.vipledger.core.domain.nlp.MerchantRuleMatcher]），
 * 用户手写规则的默认值是 `100`，因此学习规则取 **严格更大** 的值，保证用户手写规则永远先命中。
 */
const val LEARNED_RULE_PRIORITY: Int = 110

/**
 * AI 记账的「确认入账」：把用户确认后的待确认草稿置为 `CONFIRMED`，并在
 * **同一个事务内** 处理修正沉淀（PRD Requirement 4 / 7、D5#2）。
 *
 * 一次调用覆盖以下写入，全部成功才提交，避免「修正记录了一半」：
 * 1. 用确认后的字段整行更新账目，并置 `draftStatus = CONFIRMED`；
 * 2. 若用户相对解析结果改了任一字段 → 写一条 `ml_training_sample`（`USER_CORRECTION`）；
 * 3. 同一次修正再 upsert 一条 `source = LEARNED` 的 `merchant_rule`
 *    （pattern 取自标题；同商户复用已有 LEARNED 规则的 id，不产生重复规则）；
 * 4. 重算受影响账户余额（草稿此前不参与余额，确认后才计入）。
 *
 * 事务提交、修正确实写入样本后，再以**即发即弃**方式通知 [MlModelTrainer]（PRD Requirement 3）：
 * 自动训练开关默认关闭，关闭时不发生任何训练；训练失败不回滚修正、不上抛异常。
 *
 * [updated] 是界面上用户最终确认的那笔（含可能的修正）；[parsed] 是解析层原始产出，
 * 两者比对即能判定「是否修正」。
 */
class ConfirmAiEntryUseCase(
    private val txnRepository: TxnRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val mlSampleRepository: MlSampleRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val modelTrainer: MlModelTrainer,
) {

    suspend operator fun invoke(updated: Txn, parsed: ParsedEntry): AppResult<Txn> {
        val existing = txnRepository.getById(updated.id)
            ?: return AppResult.failure(AppError.NotFound("账目不存在"))
        if (existing.deletedAt != null) return AppResult.failure(AppError.NotFound("账目已删除"))
        if (existing.draftStatus != DraftStatus.PENDING) {
            return AppResult.failure(AppError.Validation("只有待确认的账目才能确认"))
        }

        val now = timeProvider.nowMillis()
        val confirmed = updated.copy(draftStatus = DraftStatus.CONFIRMED, updatedAt = now)
        val corrected = isCorrected(parsed, confirmed)

        val result = transactionRunner.inTransaction {
            val updateResult = txnRepository.update(confirmed)
            if (updateResult.isFailure) return@inTransaction updateResult

            if (corrected && confirmed.categoryId != null) {
                val sample = MlTrainingSample(
                    id = idGenerator.newId(),
                    ledgerId = confirmed.ledgerId,
                    text = sampleText(confirmed),
                    categoryId = confirmed.categoryId,
                    source = MlSampleSource.USER_CORRECTION,
                    createdAt = now,
                )
                val sampleResult = mlSampleRepository.insertAll(listOf(sample))
                if (sampleResult.isFailure) return@inTransaction sampleResult

                val pattern = learnedPatternOf(confirmed.title)
                if (pattern.isNotBlank()) {
                    val ruleResult = upsertLearnedRule(
                        ledgerId = confirmed.ledgerId,
                        pattern = pattern,
                        categoryId = confirmed.categoryId,
                        now = now,
                    )
                    if (ruleResult.isFailure) return@inTransaction ruleResult
                }
            }

            balanceSyncer.syncBalances(listOfNotNull(confirmed.accountId, confirmed.toAccountId).distinct(), now)
            AppResult.success(Unit)
        }.map { confirmed }

        // 修正已提交之后才触发自动训练（PRD Requirement 3）：开关关闭时不发生任何训练。
        // 即发即弃——[MlModelTrainer] 的实现在应用级作用域里异步训练并吞掉异常，
        // 训练失败既不会回滚这笔修正，也不会把异常抛到界面。
        if (result.isSuccess && corrected && confirmed.categoryId != null) {
            modelTrainer.onTrainingSampleWritten(confirmed.ledgerId)
        }
        return result
    }

    /**
     * 是否相对解析结果发生修正。日期不参与比对：界面上的 `occurredAt` 直接由解析出的日期派生，
     * 用户在本页不修改日期（PRD 硬约束 2「日期一律以规则层为准」），纳入比对只会引入时区误判。
     */
    private fun isCorrected(parsed: ParsedEntry, txn: Txn): Boolean =
        parsed.amount != txn.amount ||
            parsed.type != txn.type ||
            parsed.accountId != txn.accountId ||
            parsed.toAccountId != txn.toAccountId ||
            parsed.categoryId != txn.categoryId ||
            parsed.title != txn.title ||
            parsed.remark != txn.remark

    /** 训练样本文本：分类器推理用的「标题 + 备注」。 */
    private fun sampleText(txn: Txn): String =
        listOf(txn.title, txn.remark).filter { it.isNotBlank() }.joinToString(" ")

    /**
     * 同商户的修正只沉淀一条规则（PRD Requirement 7）。
     *
     * 按 `pattern`（**不看来源**）去重，而不是只找已有 LEARNED 规则：学习规则被用户在管理页
     * 「提升」为 `USER` 后来源不再是 LEARNED，若仍只按来源去重，下一次修正会再写一条同 pattern
     * 的 LEARNED 规则——既违反「同一商户重复修正不得产生重复规则」，也与管理页「提升后 AI 学习
     * 不会再覆盖它」的承诺相矛盾。
     *
     * - 已存在同 pattern 的 USER / BUILTIN 规则 → 用户已对该商户明确定规则，学习路径让位，
     *   不写入重复规则（提升后的规则因此真正受保护）；
     * - 已存在同 pattern 的 LEARNED 规则 → 复用其 id，`upsert` 走主键 REPLACE 更新而非新增。
     */
    private suspend fun upsertLearnedRule(
        ledgerId: String,
        pattern: String,
        categoryId: String,
        now: Long,
    ): AppResult<Unit> {
        val samePattern = merchantRuleRepository.listByLedger(ledgerId).filter { it.pattern == pattern }
        if (samePattern.any { it.source != RuleSource.LEARNED }) return AppResult.success(Unit)
        val existing = samePattern.firstOrNull()
        val rule = MerchantRule(
            id = existing?.id ?: idGenerator.newId(),
            ledgerId = ledgerId,
            pattern = pattern,
            matchType = RuleMatchType.CONTAINS,
            targetField = RuleTargetField.ANY,
            categoryId = categoryId,
            priority = LEARNED_RULE_PRIORITY,
            source = RuleSource.LEARNED,
            enabled = true,
            hitCount = existing?.hitCount ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return merchantRuleRepository.upsert(rule)
    }
}

/** 学习规则的关键词：取标题去空白。标题为空时不学习（调用方已保证标题非空才走到这里）。 */
fun learnedPatternOf(title: String): String = title.trim()
