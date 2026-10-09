package com.han1997.vipledger.core.database.dao

import androidx.room.*
import com.han1997.vipledger.core.database.entity.BudgetLimitEntity
import com.han1997.vipledger.core.database.entity.BudgetAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget_limit WHERE ledger_id = :ledgerId ORDER BY month, scope_key")
    fun observe(ledgerId: String): Flow<List<BudgetLimitEntity>>
    @Query("SELECT * FROM budget_limit WHERE ledger_id = :ledgerId ORDER BY month, scope_key")
    suspend fun list(ledgerId: String): List<BudgetLimitEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(rows: List<BudgetLimitEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claim(row: BudgetAlertEntity): Long
}
