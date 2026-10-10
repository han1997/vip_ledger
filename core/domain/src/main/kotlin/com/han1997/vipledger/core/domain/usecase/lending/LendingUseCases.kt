package com.han1997.vipledger.core.domain.usecase.lending

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.LendingRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.support.checkedWrite
import com.han1997.vipledger.core.domain.support.orRollback
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.LendingLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/** 借贷读用例：记录 + 关联流水 → 进度，跨表只读。 */
class ObserveLendingsUseCase(
    private val repository: LendingRepository,
    private val txns: TxnRepository,
) {
    operator fun invoke(ledgerId: String): Flow<List<LendingProgress>> = combine(
        repository.observeRecords(ledgerId),
        repository.observeLinks(ledgerId),
        txns.observe(TxnFilter(ledgerId = ledgerId)),
    ) { records, links, entries ->
        val byId = entries.associateBy { it.id }
        records.map { record ->
            val own = links.filter { it.lendingId == record.id }
            LendingProgress(record, own, own.mapNotNull { byId[it.txnId] })
        }
    }
}

/** 新建借贷：自动建立应收/应付账户并完成首次本金流转，账户、记录与流水同事务。 */
class CreateLendingUseCase(
    private val repository: LendingRepository,
    private val accounts: AccountRepository,
    private val create: CreateTxnUseCase,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        ledgerId: String,
        direction: LendingDirection,
        counterparty: String,
        principal: Money,
        cashAccountId: String,
        dueDate: LocalDate?,
        remark: String,
        occurredAt: Long?,
        title: String,
    ): AppResult<LendingRecord> = checkedWrite {
        transactions.inTransaction {
            require(counterparty.isNotBlank()) { "请填写对方名称" }
            require(principal.isPositive) { "本金必须大于 0" }
            val siblings = accounts.listByLedger(ledgerId, includeArchived = true)
            val cash = siblings.firstOrNull { it.id == cashAccountId } ?: throw IllegalArgumentException("资金账户不存在")
            require(cash.isUsable && !cash.isDebt) { "请选择可用的非负债资金账户" }
            val now = time.nowMillis()
            val lendingId = ids.newId()
            val accountId = ids.newId()
            accounts.upsert(
                Account(
                    id = accountId,
                    ledgerId = ledgerId,
                    name = uniqueAccountName(counterparty.trim(), siblings),
                    type = direction.accountType,
                    initialBalance = Money.ZERO,
                    currentBalance = Money.ZERO,
                    sortOrder = siblings.size,
                    createdAt = now,
                    updatedAt = now,
                ),
            ).orRollback()
            val record = LendingRecord(
                id = lendingId,
                ledgerId = ledgerId,
                direction = direction,
                counterparty = counterparty.trim(),
                principal = principal,
                accountId = accountId,
                dueDate = dueDate,
                remark = remark.trim(),
                closed = false,
                createdAt = now,
                updatedAt = now,
            )
            repository.save(record).orRollback()
            val draft = if (direction.isLend) {
                TxnDraft(ledgerId, TransactionType.TRANSFER, principal, cashAccountId, toAccountId = accountId, occurredAt = occurredAt, title = title)
            } else {
                TxnDraft(ledgerId, TransactionType.TRANSFER, principal, accountId, toAccountId = cashAccountId, occurredAt = occurredAt, title = title)
            }
            val txn = create(draft).orRollback()
            repository.link(LendingLink(txn.id, lendingId, LendingLinkKind.DISBURSE, now)).orRollback()
            record
        }
    }

    private fun uniqueAccountName(base: String, siblings: List<Account>): String {
        val used = siblings.map { it.name }.toSet()
        if (base !in used) return base
        var index = 2
        while ("$base-$index" in used) index++
        return "$base-$index"
    }
}

/** 部分/全部结清：本金在应收应付与资金账户间回流，直至剩余本金归零。 */
class SettleLendingUseCase(
    private val repository: LendingRepository,
    private val txns: TxnRepository,
    private val accounts: AccountRepository,
    private val create: CreateTxnUseCase,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        lendingId: String,
        cashAccountId: String,
        amount: Money,
        occurredAt: Long?,
        title: String,
    ): AppResult<Txn> = checkedWrite {
        transactions.inTransaction {
            val record = repository.record(lendingId) ?: throw IllegalArgumentException("借贷记录不存在")
            require(!record.closed) { "借贷记录已终止，不能再结清" }
            val links = repository.links(lendingId)
            val linked = txns.listByLedger(record.ledgerId).filter { it.id in links.map { link -> link.txnId }.toSet() }
            require(LendingCalculator.canSettle(record, links, linked, amount)) { "结清金额必须大于 0 且不超过剩余本金" }
            val cash = accounts.getById(cashAccountId) ?: throw IllegalArgumentException("资金账户不存在")
            require(cash.ledgerId == record.ledgerId && cash.isUsable && !cash.isDebt && cash.id != record.accountId) { "请选择可用的非负债资金账户" }
            val draft = if (record.direction.isLend) {
                TxnDraft(record.ledgerId, TransactionType.TRANSFER, amount, record.accountId, toAccountId = cashAccountId, occurredAt = occurredAt, title = title)
            } else {
                TxnDraft(record.ledgerId, TransactionType.TRANSFER, amount, cashAccountId, toAccountId = record.accountId, occurredAt = occurredAt, title = title)
            }
            val txn = create(draft).orRollback()
            repository.link(LendingLink(txn.id, lendingId, LendingLinkKind.SETTLE, time.nowMillis())).orRollback()
            txn
        }
    }
}

/** 利息：借出收息计入收入，借入付息计入支出；与本金分开记账。 */
class RecordLendingInterestUseCase(
    private val repository: LendingRepository,
    private val accounts: AccountRepository,
    private val create: CreateTxnUseCase,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        lendingId: String,
        cashAccountId: String,
        amount: Money,
        occurredAt: Long?,
        title: String,
    ): AppResult<Txn> = checkedWrite {
        transactions.inTransaction {
            val record = repository.record(lendingId) ?: throw IllegalArgumentException("借贷记录不存在")
            require(!record.closed) { "借贷记录已终止，不能再登记利息" }
            require(amount.isPositive) { "利息必须大于 0" }
            val cash = accounts.getById(cashAccountId) ?: throw IllegalArgumentException("资金账户不存在")
            require(cash.ledgerId == record.ledgerId && cash.isUsable && !cash.isDebt) { "请选择可用的非负债资金账户" }
            val type = if (record.direction.isLend) TransactionType.INCOME else TransactionType.EXPENSE
            val txn = create(TxnDraft(record.ledgerId, type, amount, cashAccountId, occurredAt = occurredAt, title = title)).orRollback()
            repository.link(LendingLink(txn.id, lendingId, LendingLinkKind.INTEREST, time.nowMillis())).orRollback()
            txn
        }
    }
}

/** 手工终止 / 恢复借贷记录。剩余本金归零会自动显示已结清，无需手工终止。 */
class CloseLendingUseCase(
    private val repository: LendingRepository,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(id: String, closed: Boolean): AppResult<Unit> = checkedWrite {
        transactions.inTransaction {
            val record = repository.record(id) ?: throw IllegalArgumentException("借贷记录不存在")
            repository.save(record.copy(closed = closed, updatedAt = time.nowMillis())).orRollback()
        }
    }
}

/**
 * 借贷关联流水的改删守卫（在事务内调用，含还原）。

 * 发放流水的金额与方向不可改，且在有结清记录时不可删除；
 * 结清流水必须保持转账方向且累计结清不超过本金；利息流水必须与借贷方向一致。
 */
class LendingTxnGuard(
    private val repository: LendingRepository,
    private val txns: TxnRepository,
) {
    companion object {
        const val LINK_INVALID = "lending_link_invalid"
        const val DIRECTION = "lending_direction"
        const val PRINCIPAL_LOCKED = "lending_principal_locked"
        const val OVER_SETTLE = "lending_over_settle"
    }

    suspend fun validate(updated: Txn): AppError? {
        val link = repository.linkForTxn(updated.id) ?: return null
        val record = repository.record(link.lendingId) ?: return AppError.Validation(LINK_INVALID)
        if (updated.isDeleted) {
            return if (link.kind == LendingLinkKind.DISBURSE) AppError.Validation(PRINCIPAL_LOCKED) else null
        }
        if (updated.ledgerId != record.ledgerId) return AppError.Validation(LINK_INVALID)
        val links = repository.links(record.id)
        val linkedIds = links.map { it.txnId }.toSet()
        val siblings = txns.listByLedger(record.ledgerId).filter { it.id != updated.id && it.id in linkedIds }
        return when (link.kind) {
            LendingLinkKind.DISBURSE -> when {
                updated.type != TransactionType.TRANSFER -> AppError.Validation(DIRECTION)
                updated.amount != record.principal -> AppError.Validation(PRINCIPAL_LOCKED)
                !isDisburseDirection(record, updated) -> AppError.Validation(DIRECTION)
                else -> null
            }
            LendingLinkKind.SETTLE -> {
                if (updated.type != TransactionType.TRANSFER || !isSettleDirection(record, updated)) return AppError.Validation(DIRECTION)
                val settledIds = links.filter { it.kind == LendingLinkKind.SETTLE }.map { it.txnId }.toSet()
                val total = Money.sum((siblings.filter { it.id in settledIds } + updated).map { it.amount })
                if (total > record.principal) AppError.Validation(OVER_SETTLE) else null
            }
            LendingLinkKind.INTEREST -> {
                val expected = if (record.direction.isLend) TransactionType.INCOME else TransactionType.EXPENSE
                if (updated.type != expected) AppError.Validation(DIRECTION) else null
            }
        }
    }

    private fun isDisburseDirection(record: LendingRecord, txn: Txn): Boolean =
        if (record.direction.isLend) txn.toAccountId == record.accountId else txn.accountId == record.accountId

    private fun isSettleDirection(record: LendingRecord, txn: Txn): Boolean =
        if (record.direction.isLend) txn.accountId == record.accountId else txn.toAccountId == record.accountId
}
