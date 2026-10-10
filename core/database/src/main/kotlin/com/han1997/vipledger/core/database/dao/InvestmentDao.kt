package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.han1997.vipledger.core.database.entity.InvestmentEntity
import com.han1997.vipledger.core.database.entity.InvestmentLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investment WHERE ledger_id=:ledgerId ORDER BY closed, created_at, id")
    fun records(ledgerId: String): Flow<List<InvestmentEntity>>

    @Query("SELECT * FROM investment WHERE ledger_id=:ledgerId ORDER BY closed, created_at, id")
    suspend fun list(ledgerId: String): List<InvestmentEntity>

    @Query("SELECT * FROM investment WHERE id=:id")
    suspend fun record(id: String): InvestmentEntity?

    @Upsert
    suspend fun save(record: InvestmentEntity)

    @Query("SELECT * FROM investment_link WHERE investment_id=:investmentId ORDER BY created_at, txn_id")
    suspend fun links(investmentId: String): List<InvestmentLinkEntity>

    @Query(
        "SELECT investment_link.* FROM investment_link INNER JOIN investment ON investment_id=investment.id WHERE investment.ledger_id=:ledgerId",
    )
    fun linksByLedger(ledgerId: String): Flow<List<InvestmentLinkEntity>>

    @Query(
        "SELECT investment_link.* FROM investment_link INNER JOIN investment ON investment_id=investment.id WHERE investment.ledger_id=:ledgerId",
    )
    suspend fun linksForLedger(ledgerId: String): List<InvestmentLinkEntity>

    @Query("SELECT * FROM investment_link WHERE txn_id=:txnId")
    suspend fun linkForTxn(txnId: String): InvestmentLinkEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun link(row: InvestmentLinkEntity)
}