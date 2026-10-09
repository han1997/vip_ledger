package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.BudgetLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface BudgetRepository {
    fun observe(ledgerId: String): Flow<List<BudgetLimit>>
    suspend fun list(ledgerId: String): List<BudgetLimit>
    suspend fun save(limits: List<BudgetLimit>): AppResult<Unit>
    suspend fun claimAlert(ledgerId: String, month: LocalDate, scopeKey: String, threshold: Int, now: Long): AppResult<Boolean>
}
