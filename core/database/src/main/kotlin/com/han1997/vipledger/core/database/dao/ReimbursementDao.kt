package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.han1997.vipledger.core.database.entity.ReimbursementEntity
import com.han1997.vipledger.core.database.entity.ReimbursementItemEntity
import com.han1997.vipledger.core.database.entity.ReimbursementReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReimbursementDao {
    @Query("SELECT * FROM reimbursement WHERE ledger_id=:ledgerId ORDER BY closed, created_at, id")
    fun projects(ledgerId: String): Flow<List<ReimbursementEntity>>

    @Query("SELECT * FROM reimbursement WHERE id=:id")
    suspend fun project(id: String): ReimbursementEntity?

    @Upsert
    suspend fun save(project: ReimbursementEntity)

    @Query("SELECT * FROM reimbursement_item WHERE reimbursement_id=:projectId ORDER BY created_at, txn_id")
    suspend fun items(projectId: String): List<ReimbursementItemEntity>

    @Query("SELECT reimbursement_item.* FROM reimbursement_item INNER JOIN reimbursement ON reimbursement_id=reimbursement.id WHERE reimbursement.ledger_id=:ledgerId")
    fun itemsByLedger(ledgerId: String): Flow<List<ReimbursementItemEntity>>

    @Query("SELECT * FROM reimbursement_item WHERE txn_id=:txnId")
    suspend fun itemForTxn(txnId: String): ReimbursementItemEntity?

    @Query("DELETE FROM reimbursement_item WHERE reimbursement_id=:projectId")
    suspend fun clearItems(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(rows: List<ReimbursementItemEntity>)

    @Query("SELECT * FROM reimbursement_receipt WHERE reimbursement_id=:projectId ORDER BY created_at, txn_id")
    suspend fun receipts(projectId: String): List<ReimbursementReceiptEntity>

    @Query("SELECT reimbursement_receipt.* FROM reimbursement_receipt INNER JOIN reimbursement ON reimbursement_id=reimbursement.id WHERE reimbursement.ledger_id=:ledgerId")
    fun receiptsByLedger(ledgerId: String): Flow<List<ReimbursementReceiptEntity>>

    @Query("SELECT * FROM reimbursement_receipt WHERE txn_id=:txnId")
    suspend fun receiptForTxn(txnId: String): ReimbursementReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(row: ReimbursementReceiptEntity)
}
