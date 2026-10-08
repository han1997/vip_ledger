package com.han1997.vipledger.core.database.dao

import androidx.room.*
import com.han1997.vipledger.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoBookDao {
    @Query("SELECT * FROM auto_config WHERE id = 1") fun observeConfig(): Flow<AutoConfigEntity?>
    @Query("SELECT * FROM auto_config WHERE id = 1") suspend fun config(): AutoConfigEntity?
    @Upsert suspend fun saveConfig(value: AutoConfigEntity)
    @Query("SELECT * FROM auto_binding WHERE ledgerId = :ledgerId") fun observeBindings(ledgerId: String): Flow<List<AutoBindingEntity>>
    @Query("SELECT * FROM auto_binding WHERE ledgerId = :ledgerId") suspend fun bindings(ledgerId: String): List<AutoBindingEntity>
    @Upsert suspend fun bind(value: AutoBindingEntity)
    @Delete suspend fun unbind(value: AutoBindingEntity)
    @Query("SELECT * FROM auto_candidate WHERE ledgerId = :ledgerId ORDER BY occurredAt DESC")
    fun observeCandidates(ledgerId: String): Flow<List<AutoCandidateEntity>>
    @Query("SELECT * FROM auto_candidate WHERE id = :id") suspend fun candidate(id: String): AutoCandidateEntity?
    @Query("SELECT * FROM auto_candidate WHERE ledgerId = :ledgerId AND occurredAt BETWEEN :start AND :end")
    suspend fun nearby(ledgerId: String, start: Long, end: Long): List<AutoCandidateEntity>
    @Upsert suspend fun saveCandidate(value: AutoCandidateEntity)
    @Query("SELECT candidateId FROM auto_evidence WHERE ledgerId = :ledgerId AND `key` = :key")
    suspend fun evidence(ledgerId: String, key: String): String?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addEvidence(value: AutoEvidenceEntity)
}
