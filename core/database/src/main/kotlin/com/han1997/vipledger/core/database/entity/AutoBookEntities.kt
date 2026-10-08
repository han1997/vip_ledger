package com.han1997.vipledger.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "auto_config")
data class AutoConfigEntity(
    @PrimaryKey val id: Int = 1,
    val ledgerId: String, val enabled: Boolean, val ocrEnabled: Boolean,
    val enabledApps: String, val verifiedApps: String,
)
@Entity(tableName = "auto_binding", primaryKeys = ["ledgerId", "app", "token"])
data class AutoBindingEntity(val ledgerId: String, val app: String, val token: String, val accountId: String)
@Entity(tableName = "auto_candidate", indices = [Index(value = ["ledgerId", "occurredAt"])])
data class AutoCandidateEntity(
    @PrimaryKey val id: String, val ledgerId: String, val app: String, val source: String,
    val occurredAt: Long, val facts: String, val accountId: String?, val targetAccountId: String?,
    val status: String, val reason: String, val txnId: String?, val refundOfTxnId: String?,
    val duplicateTxnId: String?,
)
@Entity(tableName = "auto_evidence", primaryKeys = ["ledgerId", "key"], indices = [Index("candidateId")])
data class AutoEvidenceEntity(val ledgerId: String, val key: String, val candidateId: String)
