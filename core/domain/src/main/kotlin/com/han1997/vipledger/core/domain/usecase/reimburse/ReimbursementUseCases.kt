package com.han1997.vipledger.core.domain.usecase.reimburse

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.ReimbursementRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.support.checkedWrite
import com.han1997.vipledger.core.domain.support.orRollback
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 报销读用例：项目 + 关联原支出 + 到账退款 → 进度，跨表只读不同步写。 */
class ObserveReimbursementsUseCase(
    private val repository: ReimbursementRepository,
    private val txns: TxnRepository,
) {
    operator fun invoke(ledgerId: String): Flow<List<ReimbursementProgress>> = combine(
        repository.observeProjects(ledgerId),
        repository.observeItems(ledgerId),
        repository.observeReceipts(ledgerId),
        txns.observe(TxnFilter(ledgerId = ledgerId, types = setOf(TransactionType.EXPENSE, TransactionType.REFUND))),
    ) { projects, items, receipts, entries ->
        val byId = entries.associateBy { it.id }
        val activeItems = items.filter { byId.containsKey(it.txnId) }
        val activeReceipts = receipts.filter { byId.containsKey(it.txnId) }
        projects.map { project ->
            ReimbursementProgress(
                project = project,
                items = activeItems.filter { it.reimbursementId == project.id }.mapNotNull { byId[it.txnId] },
                receipts = activeReceipts.filter { it.reimbursementId == project.id }.mapNotNull { byId[it.txnId] },
            )
        }
    }
}

/** 新建或修改报销项目：项目与多笔费用关联原子替换；已到账的原支出不能移出。 */
class SaveReimbursementUseCase(
    private val repository: ReimbursementRepository,
    private val txns: TxnRepository,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        id: String?,
        ledgerId: String,
        title: String,
        counterparty: String,
        remark: String,
        itemTxnIds: List<String>,
    ): AppResult<Unit> = checkedWrite {
        transactions.inTransaction {
            require(title.isNotBlank()) { "报销名称不能为空" }
            val targetIds = itemTxnIds.distinct()
            require(targetIds.isNotEmpty()) { "请至少选择一笔支出" }
            val all = txns.listByLedger(ledgerId)
            targetIds.forEach { txnId ->
                val txn = all.firstOrNull { it.id == txnId } ?: throw IllegalArgumentException("所选支出不存在")
                require(txn.type == TransactionType.EXPENSE && !txn.isDeleted && txn.draftStatus == DraftStatus.CONFIRMED) { "只有已确认的支出可以加入报销" }
                val owner = repository.itemForTxn(txnId)
                require(owner == null || owner.reimbursementId == id) { "该支出已属于其他报销项目" }
            }
            val existing = id?.let { repository.project(it) }
            require(existing == null || existing.ledgerId == ledgerId) { "报销项目不存在" }
            if (existing != null) {
                val receiptIds = repository.receipts(existing.id).map { it.txnId }.toSet()
                all.filter { it.id in receiptIds }.forEach { receipt ->
                    require(receipt.refundOfTxnId != null && receipt.refundOfTxnId in targetIds) { "已到账的原支出不能移出报销项目" }
                }
            }
            val now = time.nowMillis()
            repository.save(
                Reimbursement(
                    id = id ?: ids.newId(),
                    ledgerId = ledgerId,
                    title = title.trim(),
                    counterparty = counterparty.trim(),
                    remark = remark.trim(),
                    closed = existing?.closed ?: false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
                targetIds,
            ).orRollback()
        }
    }
}

/**
 * 登记报销到账：生成一条关联原支出的 REFUND 流水并建立到账关联。
 * 单笔不超过该原支出的待报销余额，全部退款与报销到账共用原消费金额上限。
 */
class ReceiveReimbursementUseCase(
    private val repository: ReimbursementRepository,
    private val txns: TxnRepository,
    private val accounts: AccountRepository,
    private val create: CreateTxnUseCase,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(
        projectId: String,
        itemTxnId: String,
        amount: Money,
        accountId: String,
        occurredAt: Long?,
        title: String,
    ): AppResult<Txn> = checkedWrite {
        transactions.inTransaction {
            val project = repository.project(projectId) ?: throw IllegalArgumentException("报销项目不存在")
            require(!project.closed) { "报销项目已终止，不能再登记到账" }
            val item = repository.itemForTxn(itemTxnId)
            require(item != null && item.reimbursementId == projectId) { "该支出不属于此报销项目" }
            val original = txns.getById(itemTxnId) ?: throw IllegalArgumentException("原支出不存在")
            require(original.type == TransactionType.EXPENSE && !original.isDeleted && original.draftStatus == DraftStatus.CONFIRMED) { "原支出不可用" }
            val receiptIds = repository.receipts(projectId).map { it.txnId }.toSet()
            val received = txns.listByLedger(project.ledgerId).filter { it.id in receiptIds }
            val remaining = ReimbursementCalculator.remainingFor(original, received)
            require(amount.isPositive && amount <= remaining) { "到账金额必须大于 0 且不超过该笔支出的待报销金额" }
            val account = accounts.getById(accountId) ?: throw IllegalArgumentException("到账账户不存在")
            require(account.ledgerId == project.ledgerId && account.isUsable) { "到账账户不可用" }
            val txn = create(
                TxnDraft(
                    ledgerId = project.ledgerId,
                    type = TransactionType.REFUND,
                    amount = amount,
                    accountId = accountId,
                    occurredAt = occurredAt,
                    title = title,
                    categoryId = original.categoryId,
                    excludeFromStats = original.excludeFromStats,
                    refundOfTxnId = itemTxnId,
                ),
            ).orRollback()
            repository.addReceipt(ReimbursementReceipt(projectId, txn.id)).orRollback()
            txn
        }
    }
}

/** 终止 / 恢复报销项目。终止只影响后续到账，不改动既有账目。 */
class CloseReimbursementUseCase(
    private val repository: ReimbursementRepository,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(id: String, closed: Boolean): AppResult<Unit> = checkedWrite {
        transactions.inTransaction {
            val project = repository.project(id) ?: throw IllegalArgumentException("报销项目不存在")
            val itemTxnIds = repository.items(project.id).map { it.txnId }
            repository.save(project.copy(closed = closed, updatedAt = time.nowMillis()), itemTxnIds).orRollback()
        }
    }
}

/**
 * 报销关联流水的改删守卫（在事务内调用，含还原）。

 * 已加入项目的支出必须保持「已确认的支出且未删除」，需先从项目移除；
 * 到账流水必须保持退款方向且仍指向项目内的原支出。
 */
class ReimbursementTxnGuard(
    private val repository: ReimbursementRepository,
    private val txns: TxnRepository,
) {
    companion object {
        const val ITEM_LOCKED = "reimbursement_item_locked"
        const val RECEIPT_LOCKED = "reimbursement_receipt_locked"
    }

    suspend fun validate(updated: Txn): AppError? {
        repository.receiptForTxn(updated.id)?.let { receipt ->
            if (updated.isDeleted) return null
            val project = repository.project(receipt.reimbursementId) ?: return AppError.Validation(RECEIPT_LOCKED)
            if (updated.ledgerId != project.ledgerId) return AppError.Validation(RECEIPT_LOCKED)
            if (updated.type != TransactionType.REFUND || updated.refundOfTxnId == null) return AppError.Validation(RECEIPT_LOCKED)
            val item = repository.itemForTxn(updated.refundOfTxnId!!) ?: return AppError.Validation(RECEIPT_LOCKED)
            if (item.reimbursementId != project.id) return AppError.Validation(RECEIPT_LOCKED)
            return null
        }
        repository.itemForTxn(updated.id) ?: return null
        if (updated.isDeleted) return AppError.Validation(ITEM_LOCKED)
        if (updated.type != TransactionType.EXPENSE || updated.draftStatus != DraftStatus.CONFIRMED) {
            return AppError.Validation(ITEM_LOCKED)
        }
        return null
    }
}
