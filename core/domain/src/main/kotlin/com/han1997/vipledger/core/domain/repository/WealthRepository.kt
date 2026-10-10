package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.*
import kotlinx.coroutines.flow.Flow

interface WealthRepository {
    fun observeGoals(ledgerId: String): Flow<List<SavingsGoal>>
    fun observeLinks(ledgerId: String): Flow<List<SavingsLink>>
    suspend fun goal(id: String): SavingsGoal?
    suspend fun links(goalId: String): List<SavingsLink>
    suspend fun linkForTxn(txnId: String): SavingsLink?
    suspend fun saveGoal(goal: SavingsGoal): AppResult<Unit>
    suspend fun link(link: SavingsLink): AppResult<Unit>
    fun observeCards(ledgerId: String): Flow<List<HomeCardSetting>>
    suspend fun saveCards(ledgerId: String, rows: List<HomeCardSetting>): AppResult<Unit>
    fun observeBaselines(ledgerId: String): Flow<List<AssetBaseline>>
    suspend fun ensureBaselines(accounts: List<Account>, now: Long): AppResult<Unit>
}
