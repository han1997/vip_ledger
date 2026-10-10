package com.han1997.vipledger.core.data.repository

import androidx.room.withTransaction
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.ReimbursementEntity
import com.han1997.vipledger.core.database.entity.ReimbursementItemEntity
import com.han1997.vipledger.core.database.entity.ReimbursementReceiptEntity
import com.han1997.vipledger.core.domain.model.Reimbursement
import com.han1997.vipledger.core.domain.model.ReimbursementItem
import com.han1997.vipledger.core.domain.model.ReimbursementReceipt
import com.han1997.vipledger.core.domain.repository.ReimbursementRepository
import kotlinx.coroutines.flow.map

class ReimbursementRepositoryImpl(private val db: LedgerDatabase) : ReimbursementRepository {
    private val dao = db.reimbursementDao()

    override fun observeProjects(ledgerId: String) = dao.projects(ledgerId).map { rows -> rows.map(::domainProject) }

    override fun observeItems(ledgerId: String) = dao.itemsByLedger(ledgerId).map { rows -> rows.map(::domainItem) }

    override fun observeReceipts(ledgerId: String) = dao.receiptsByLedger(ledgerId).map { rows -> rows.map(::domainReceipt) }

    override suspend fun project(id: String) = dao.project(id)?.let(::domainProject)

    override suspend fun items(projectId: String) = dao.items(projectId).map(::domainItem)

    override suspend fun receipts(projectId: String) = dao.receipts(projectId).map(::domainReceipt)

    override suspend fun itemForTxn(txnId: String) = dao.itemForTxn(txnId)?.let(::domainItem)

    override suspend fun receiptForTxn(txnId: String) = dao.receiptForTxn(txnId)?.let(::domainReceipt)

    override suspend fun save(project: Reimbursement, itemTxnIds: List<String>) = dataResult {
        db.withTransaction {
            dao.save(project.toEntity())
            dao.clearItems(project.id)
            if (itemTxnIds.isNotEmpty()) {
                dao.insertItems(itemTxnIds.map { ReimbursementItemEntity(it, project.id, project.updatedAt) })
            }
        }
    }

    override suspend fun addReceipt(receipt: ReimbursementReceipt) = dataResult {
        dao.insertReceipt(ReimbursementReceiptEntity(receipt.txnId, receipt.reimbursementId, System.currentTimeMillis()))
    }

    private fun domainProject(row: ReimbursementEntity) = Reimbursement(row.id, row.ledgerId, row.title, row.counterparty, row.remark, row.closed, row.createdAt, row.updatedAt)

    private fun domainItem(row: ReimbursementItemEntity) = ReimbursementItem(row.reimbursementId, row.txnId)

    private fun domainReceipt(row: ReimbursementReceiptEntity) = ReimbursementReceipt(row.reimbursementId, row.txnId)

    private fun Reimbursement.toEntity() = ReimbursementEntity(id, ledgerId, title, counterparty, remark, closed, createdAt, updatedAt)
}
