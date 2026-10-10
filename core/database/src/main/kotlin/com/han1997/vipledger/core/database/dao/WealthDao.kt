package com.han1997.vipledger.core.database.dao

import androidx.room.*
import com.han1997.vipledger.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WealthDao {
    @Query("SELECT * FROM savings_goal WHERE ledger_id=:ledgerId ORDER BY created_at,id")
    fun goals(ledgerId: String): Flow<List<SavingsGoalEntity>>
    @Query("SELECT * FROM savings_goal WHERE id=:id") suspend fun goal(id: String): SavingsGoalEntity?
    @Upsert suspend fun save(goal: SavingsGoalEntity)
    @Query("SELECT savings_link.* FROM savings_link INNER JOIN savings_goal ON goal_id=savings_goal.id WHERE ledger_id=:ledgerId")
    fun linksByLedger(ledgerId: String): Flow<List<SavingsLinkEntity>>
    @Query("SELECT * FROM savings_link WHERE goal_id=:goalId") suspend fun links(goalId: String): List<SavingsLinkEntity>
    @Query("SELECT * FROM savings_link WHERE txn_id=:id") suspend fun linkForTxn(id: String): SavingsLinkEntity?
    @Insert suspend fun link(row: SavingsLinkEntity)
    @Query("SELECT * FROM home_card WHERE ledger_id=:ledgerId ORDER BY position,card_key") fun cards(ledgerId: String): Flow<List<HomeCardEntity>>
    @Query("DELETE FROM home_card WHERE ledger_id=:ledgerId") suspend fun clearCards(ledgerId: String)
    @Insert suspend fun cards(rows: List<HomeCardEntity>)
    @Query("SELECT * FROM asset_baseline WHERE ledger_id=:ledgerId ORDER BY captured_at,account_id") fun baselines(ledgerId: String): Flow<List<AssetBaselineEntity>>
    @Query("SELECT account_id FROM asset_baseline WHERE ledger_id=:ledgerId") suspend fun baselineIds(ledgerId: String): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun baselines(rows: List<AssetBaselineEntity>)
}
