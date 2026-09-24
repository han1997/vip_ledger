package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.MerchantRuleDao
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.repository.MerchantRuleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MerchantRuleRepositoryImpl @Inject constructor(
    private val merchantRuleDao: MerchantRuleDao,
) : MerchantRuleRepository {

    override fun observeByLedger(ledgerId: String): Flow<List<MerchantRule>> =
        merchantRuleDao.observeByLedger(ledgerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listByLedger(ledgerId: String): List<MerchantRule> =
        merchantRuleDao.listByLedger(ledgerId).map { it.toDomain() }

    override suspend fun listEnabledByPriority(ledgerId: String): List<MerchantRule> =
        merchantRuleDao.listEnabledByPriority(ledgerId).map { it.toDomain() }

    override suspend fun upsert(rule: MerchantRule): AppResult<Unit> =
        dataResult { merchantRuleDao.upsert(rule.toEntity()) }

    override suspend fun incrementHitCount(ruleId: String, updatedAt: Long): AppResult<Unit> =
        dataResult { merchantRuleDao.incrementHitCount(ruleId, updatedAt) }

    override suspend fun delete(ruleId: String): AppResult<Unit> = dataResult { merchantRuleDao.delete(ruleId) }
}