package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.MerchantRule
import kotlinx.coroutines.flow.Flow

interface MerchantRuleRepository {
    fun observeByLedger(ledgerId: String): Flow<List<MerchantRule>>

    suspend fun listByLedger(ledgerId: String): List<MerchantRule>

    /** 按 priority 升序返回启用中的规则，供 M6 的本地匹配使用。 */
    suspend fun listEnabledByPriority(ledgerId: String): List<MerchantRule>

    suspend fun upsert(rule: MerchantRule): AppResult<Unit>

    suspend fun incrementHitCount(ruleId: String, updatedAt: Long): AppResult<Unit>

    suspend fun delete(ruleId: String): AppResult<Unit>
}