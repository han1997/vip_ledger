package com.han1997.vipledger.core.domain.usecase.recurring

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.Flow

/** 订阅账本下的全部周期规则（规则列表页用）。 */
class ObserveRecurringRulesUseCase(private val repository: RecurringRuleRepository) {
    operator fun invoke(ledgerId: String): Flow<List<RecurringRule>> = repository.observeByLedger(ledgerId)
}

/** 启用 / 停用周期规则。 */
class SetRecurringRuleEnabledUseCase(
    private val repository: RecurringRuleRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(ruleId: String, enabled: Boolean): AppResult<Unit> {
        val rule = repository.getById(ruleId)
            ?: return AppResult.failure(AppError.NotFound("周期规则不存在"))
        if (rule.deletedAt != null) return AppResult.failure(AppError.NotFound("周期规则已删除"))
        return repository.setEnabled(ruleId, enabled, timeProvider.nowMillis())
    }
}

/** 删除（软删）周期规则；已经生成的流水保留。 */
class DeleteRecurringRuleUseCase(
    private val repository: RecurringRuleRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(ruleId: String): AppResult<Unit> {
        val rule = repository.getById(ruleId)
            ?: return AppResult.failure(AppError.NotFound("周期规则不存在"))
        if (rule.deletedAt != null) return AppResult.failure(AppError.NotFound("周期规则已删除"))
        return repository.softDelete(ruleId, timeProvider.nowMillis())
    }
}
