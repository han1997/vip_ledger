package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Reimbursement
import com.han1997.vipledger.core.domain.model.ReimbursementItem
import com.han1997.vipledger.core.domain.model.ReimbursementReceipt
import kotlinx.coroutines.flow.Flow

interface ReimbursementRepository {
    fun observeProjects(ledgerId: String): Flow<List<Reimbursement>>

    fun observeItems(ledgerId: String): Flow<List<ReimbursementItem>>

    fun observeReceipts(ledgerId: String): Flow<List<ReimbursementReceipt>>

    suspend fun project(id: String): Reimbursement?

    suspend fun items(projectId: String): List<ReimbursementItem>

    suspend fun receipts(projectId: String): List<ReimbursementReceipt>

    suspend fun itemForTxn(txnId: String): ReimbursementItem?

    suspend fun receiptForTxn(txnId: String): ReimbursementReceipt?

    /** 原位保存项目并整体替换其费用关联，同事务完成。 */
    suspend fun save(project: Reimbursement, itemTxnIds: List<String>): AppResult<Unit>

    suspend fun addReceipt(receipt: ReimbursementReceipt): AppResult<Unit>
}
