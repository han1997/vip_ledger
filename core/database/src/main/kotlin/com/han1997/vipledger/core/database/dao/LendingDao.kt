package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.han1997.vipledger.core.database.entity.LendingEntity
import com.han1997.vipledger.core.database.entity.LendingLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LendingDao {
    @Query("SELECT * FROM lending WHERE ledger_id=:ledgerId ORDER BY closed, created_at, id")
    fun records(ledgerId: String): Flow<List<LendingEntity>>

    @Query("SELECT * FROM lending WHERE id=:id")
    suspend fun record(id: String): LendingEntity?

    @Upsert
    suspend fun save(record: LendingEntity)

    @Query("SELECT * FROM lending_link WHERE lending_id=:lendingId ORDER BY created_at, txn_id")
    suspend fun links(lendingId: String): List<LendingLinkEntity>

    @Query("SELECT lending_link.* FROM lending_link INNER JOIN lending ON lending_id=lending.id WHERE lending.ledger_id=:ledgerId")
    fun linksByLedger(ledgerId: String): Flow<List<LendingLinkEntity>>

    @Query("SELECT * FROM lending_link WHERE txn_id=:txnId")
    suspend fun linkForTxn(txnId: String): LendingLinkEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun link(row: LendingLinkEntity)
}
