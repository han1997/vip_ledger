package com.han1997.vipledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.han1997.vipledger.core.database.entity.TxnTagEntity

@Dao
interface TxnTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<TxnTagEntity>)

    @Query("DELETE FROM txn_tag WHERE txn_id = :txnId")
    suspend fun deleteByTxnId(txnId: String)

    @Query("DELETE FROM txn_tag WHERE txn_id IN (:txnIds)")
    suspend fun deleteByTxnIds(txnIds: List<String>)

    @Query("SELECT * FROM txn_tag WHERE txn_id IN (:txnIds)")
    suspend fun listByTxnIds(txnIds: List<String>): List<TxnTagEntity>

    @Query("SELECT * FROM txn_tag WHERE tag_id = :tagId")
    suspend fun listByTagId(tagId: String): List<TxnTagEntity>
}