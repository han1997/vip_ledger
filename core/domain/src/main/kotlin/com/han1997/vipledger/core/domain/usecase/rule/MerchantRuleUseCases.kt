package com.han1997.vipledger.core.domain.usecase.rule

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.MerchantRuleRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField
import kotlinx.coroutines.flow.Flow

/** 用户手写规则的默认优先级；`merchant_rule` 按 priority 升序命中，越小越优先。 */
const val DEFAULT_USER_RULE_PRIORITY: Int = 100

/** 订阅账本下的全部商户规则（PRD Requirement 9 的规则管理页用）。 */
class ObserveMerchantRulesUseCase(private val repository: MerchantRuleRepository) {
    operator fun invoke(ledgerId: String): Flow<List<MerchantRule>> = repository.observeByLedger(ledgerId)
}

/** 新建 / 编辑商户规则的入参。 */
data class MerchantRuleInput(
    val ledgerId: String,
    val ruleId: String? = null,
    val pattern: String,
    val matchType: RuleMatchType = RuleMatchType.CONTAINS,
    val targetField: RuleTargetField = RuleTargetField.ANY,
    val categoryId: String,
    val priority: Int = DEFAULT_USER_RULE_PRIORITY,
    val enabled: Boolean = true,
    /** 保存时是否把已学习 / 内置规则「提升」为用户手写规则（PRD Requirement 9）。 */
    val promoteToUser: Boolean = false,
)

/**
 * 正则是否可编译。
 *
 * 与 [com.han1997.vipledger.core.domain.nlp.MerchantRuleMatcher] 的判据一致：那里对无法编译的正则
 * 静默跳过该条规则。管理页用同一个判据在**保存前**拦下非法正则，避免存下一条永远不命中的规则。
 */
fun isValidRuleRegex(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

/**
 * 与仓库无关的语法校验：界面可即时调用（编辑框即时提示），用例保存前也调用一次。
 *
 * 两者共用同一份判据，避免「界面放行、保存再拒绝」或「界面拒绝、保存放行」的漂移。
 */
fun validateMerchantRuleSyntax(
    pattern: String,
    matchType: RuleMatchType,
    categoryId: String?,
    priority: Int,
): AppError? {
    if (pattern.isBlank()) return AppError.Validation("规则关键词不能为空")
    if (matchType == RuleMatchType.REGEX && !isValidRuleRegex(pattern.trim())) {
        return AppError.Validation("正则表达式无效，请检查语法")
    }
    if (categoryId.isNullOrBlank()) return AppError.Validation("请选择目标分类")
    if (priority < 1) return AppError.Validation("优先级必须是正整数")
    return null
}

/**
 * 新建 / 编辑商户规则。
 *
 * - 新建的规则一律标 [RuleSource.USER]；
 * - 编辑时保留原有 `source` / `hitCount` / `createdAt`，只在 [MerchantRuleInput.promoteToUser] 为真时
 *   把来源改为 [RuleSource.USER]（学习规则被「提升」为用户手写规则后，学习路径不再复用它的 id）；
 * - 目标分类必须存在于本账本，避免规则指向一个已删除 / 外账本的分类。
 */
class SaveMerchantRuleUseCase(
    private val repository: MerchantRuleRepository,
    private val categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(input: MerchantRuleInput): AppResult<MerchantRule> {
        validateMerchantRuleSyntax(input.pattern, input.matchType, input.categoryId, input.priority)
            ?.let { return AppResult.failure(it) }

        val existing = input.ruleId?.let { id ->
            repository.listByLedger(input.ledgerId).firstOrNull { it.id == id }
        }
        if (input.ruleId != null && existing == null) {
            return AppResult.failure(AppError.NotFound("规则不存在"))
        }
        if (existing != null && existing.ledgerId != input.ledgerId) {
            return AppResult.failure(AppError.Validation("规则不属于当前账本"))
        }

        val category = categoryRepository.listByLedger(input.ledgerId, includeArchived = true)
            .firstOrNull { it.id == input.categoryId && it.deletedAt == null }
            ?: return AppResult.failure(AppError.Validation("分类不存在"))
        if (category.ledgerId != input.ledgerId) {
            return AppResult.failure(AppError.Validation("分类不属于当前账本"))
        }

        val now = timeProvider.nowMillis()
        val source = when {
            existing == null -> RuleSource.USER
            input.promoteToUser -> RuleSource.USER
            else -> existing.source
        }
        val rule = MerchantRule(
            id = existing?.id ?: idGenerator.newId(),
            ledgerId = input.ledgerId,
            pattern = input.pattern.trim(),
            matchType = input.matchType,
            targetField = input.targetField,
            categoryId = input.categoryId,
            priority = input.priority,
            source = source,
            enabled = input.enabled,
            hitCount = existing?.hitCount ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return repository.upsert(rule).map { rule }
    }
}

/** 启用 / 停用规则：保留其余字段与命中次数。 */
class SetMerchantRuleEnabledUseCase(
    private val repository: MerchantRuleRepository,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(ledgerId: String, ruleId: String, enabled: Boolean): AppResult<Unit> {
        val rule = repository.listByLedger(ledgerId).firstOrNull { it.id == ruleId }
            ?: return AppResult.failure(AppError.NotFound("规则不存在"))
        return repository.upsert(rule.copy(enabled = enabled, updatedAt = timeProvider.nowMillis()))
    }
}

/** 删除规则（硬删；规则是配置，不参与账目统计，删掉不留悬空引用）。 */
class DeleteMerchantRuleUseCase(private val repository: MerchantRuleRepository) {
    suspend operator fun invoke(ruleId: String): AppResult<Unit> = repository.delete(ruleId)
}
