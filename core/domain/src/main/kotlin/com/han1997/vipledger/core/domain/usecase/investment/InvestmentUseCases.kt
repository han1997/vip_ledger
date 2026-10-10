package com.han1997.vipledger.core.domain.usecase.investment

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.InvestmentCalculator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Investment
import com.han1997.vipledger.core.domain.model.InvestmentLink
import com.han1997.vipledger.core.domain.model.InvestmentProgress
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.InvestmentRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.support.checkedWrite
import com.han1997.vipledger.core.domain.support.orRollback
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 理财读用例：台账 + 关联流水 → 进度，跨表只读。 */
class ObserveInvestmentsUseCase(
    private val repository: InvestmentRepository,
    private val txns: TxnRepository,
) {
    operator fun invoke(ledgerId: String): Flow<List<InvestmentProgress>> = combine(
        repository.observeInvestments(ledgerId),
        repository.observeLinks(ledgerId),
        txns.observe(TxnFilter(ledgerId = ledgerId)),
    ) { records, links, entries ->
        val byId = entries.associateBy { it.id }
        records.map { record ->
            val own = links.filter { it.investmentId == record.id }
            InvestmentProgress(record, own, own.mapNotNull { byId[it.txnId] })
        }
    }
}

/** 新建理财：自动建立投资账户并完成首次投入，账户、台账与流水同事务。 */
class CreateInvestmentUseCase(
    private val repository: InvestmentRepository,
    private val accounts: AccountRepository,
    private val create: CreateTxnUseCase,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        ledgerId: String,
        name: String,
        cashAccountId: String,
        amount: Money,
        remark: String = "",
        occurredAt: Long? = null,
        title: String = "",
    ): AppResult<Investment> = checkedWrite {
        transactions.inTransaction {
            require(name.isNotBlank()) { "请填写理财名称" }
            require(amount.isPositive) { "投入金额必须大于 0" }
            val siblings = accounts.listByLedger(ledgerId, includeArchived = true)
            val cash = siblings.firstOrNull { it.id == cashAccountId } ?: throw IllegalArgumentException("资金账户不存在")
            require(cash.isUsable && !cash.isDebt) { "请选择可用的非负债资金账户" }
            val now = time.nowMillis()
            val investmentId = ids.newId()
            val accountId = ids.newId()
            accounts.upsert(
                Account(
                    id = accountId,
                    ledgerId = ledgerId,
                    name = uniqueAccountName(name.trim(), siblings),
                    type = AccountType.INVESTMENT,
                    initialBalance = Money.ZERO,
                    currentBalance = Money.ZERO,
                    sortOrder = siblings.size,
                    createdAt = now,
                    updatedAt = now,
                ),
            ).orRollback()
            val record = Investment(
                id = investmentId,
                ledgerId = ledgerId,
                name = name.trim(),
                accountId = accountId,
                remark = remark.trim(),
                closed = false,
                createdAt = now,
                updatedAt = now,
            )
            repository.save(record).orRollback()
            val txn = create(
                TxnDraft(
                    ledgerId = ledgerId,
                    type = TransactionType.TRANSFER,
                    amount = amount,
                    accountId = cashAccountId,
                    toAccountId = accountId,
                    occurredAt = occurredAt,
                    title = title.ifBlank { record.name },
                ),
            ).orRollback()
            repository.link(InvestmentLink(txn.id, investmentId, InvestmentLinkKind.INVEST, now)).orRollback()
            record
        }
    }

    private fun uniqueAccountName(base: String, siblings: List<Account>): String {
        val taken = siblings.map { it.name }.toSet()
        if (base !in taken) return base
        var index = 2
        while ("$base-$index" in taken) index++
        return "$base-$index"
    }
}

/** 追加投入本金：资金账户 → 投资账户转账。 */
class InvestMoreUseCase(
    private val repository: InvestmentRepository,
    private val accounts: AccountRepository,
    private val create: CreateTxnUseCase,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        investmentId: String,
        cashAccountId: String,
        amount: Money,
        occurredAt: Long? = null,
        title: String = "",
    ): AppResult<Txn> = checkedWrite {
        transactions.inTransaction {
            val record = repository.investment(investmentId) ?: throw IllegalArgumentException("理财台账不存在")
            require(!record.closed) { "理财台账已结束，不能继续投入" }
            require(amount.isPositive) { "投入金额必须大于 0" }
            val cash = accounts.getById(cashAccountId) ?: throw IllegalArgumentException("资金账户不存在")
            require(cash.ledgerId == record.ledgerId && cash.isUsable && !cash.isDebt) { "请选择可用的非负债资金账户" }
            val txn = create(
                TxnDraft(
                    ledgerId = record.ledgerId,
                    type = TransactionType.TRANSFER,
                    amount = amount,
                    accountId = cashAccountId,
                    toAccountId = record.accountId,
                    occurredAt = occurredAt,
                    title = title.ifBlank { record.name },
                ),
            ).orRollback()
            repository.link(InvestmentLink(txn.id, record.id, InvestmentLinkKind.INVEST, time.nowMillis())).orRollback()
            txn
        }
    }
}

/** 赎回本金：投资账户 → 资金账户转账，不得超过在投本金。 */
class RedeemInvestmentUseCase(
    private val repository: InvestmentRepository,
    private val txns: TxnRepository,
    private val accounts: AccountRepository,
    private val create: CreateTxnUseCase,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        investmentId: String,
        cashAccountId: String,
        amount: Money,
        occurredAt: Long? = null,
        title: String = "",
    ): AppResult<Txn> = checkedWrite {
        transactions.inTransaction {
            val record = repository.investment(investmentId) ?: throw IllegalArgumentException("理财台账不存在")
            require(amount.isPositive) { "赎回金额必须大于 0" }
            val byId = txns.listByLedger(record.ledgerId).associateBy { it.id }
            val outstanding = InvestmentCalculator.redeemable(repository.links(record.id), byId)
            require(amount <= outstanding) { "赎回金额不能超过在投本金" }
            val cash = accounts.getById(cashAccountId) ?: throw IllegalArgumentException("资金账户不存在")
            require(cash.ledgerId == record.ledgerId && cash.isUsable && !cash.isDebt) { "请选择可用的非负债资金账户" }
            val txn = create(
                TxnDraft(
                    ledgerId = record.ledgerId,
                    type = TransactionType.TRANSFER,
                    amount = amount,
                    accountId = record.accountId,
                    toAccountId = cashAccountId,
                    occurredAt = occurredAt,
                    title = title.ifBlank { record.name },
                ),
            ).orRollback()
            repository.link(InvestmentLink(txn.id, record.id, InvestmentLinkKind.REDEEM, time.nowMillis())).orRollback()
            txn
        }
    }
}

/** 登记已实现收益或损失/费用：收益记收入，损失/费用记支出。 */
class RecordInvestmentResultUseCase(
    private val repository: InvestmentRepository,
    private val accounts: AccountRepository,
    private val create: CreateTxnUseCase,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        investmentId: String,
        cashAccountId: String,
        amount: Money,
        kind: InvestmentLinkKind,
        occurredAt: Long? = null,
        title: String = "",
    ): AppResult<Txn> = checkedWrite {
        transactions.inTransaction {
            require(kind == InvestmentLinkKind.INCOME || kind == InvestmentLinkKind.EXPENSE) {
                "只能登记收益或损失"
            }
            val record = repository.investment(investmentId) ?: throw IllegalArgumentException("理财台账不存在")
            require(amount.isPositive) { "金额必须大于 0" }
            val cash = accounts.getById(cashAccountId) ?: throw IllegalArgumentException("资金账户不存在")
            require(cash.ledgerId == record.ledgerId && cash.isUsable && !cash.isDebt) { "请选择可用的非负债资金账户" }
            val type = if (kind == InvestmentLinkKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
            val txn = create(
                TxnDraft(
                    ledgerId = record.ledgerId,
                    type = type,
                    amount = amount,
                    accountId = cashAccountId,
                    occurredAt = occurredAt,
                    title = title.ifBlank { record.name },
                ),
            ).orRollback()
            repository.link(InvestmentLink(txn.id, record.id, kind, time.nowMillis())).orRollback()
            txn
        }
    }
}

/** 手工结束 / 恢复理财台账；在投本金归零会自动显示为已赎回。 */
class CloseInvestmentUseCase(
    private val repository: InvestmentRepository,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(id: String, closed: Boolean): AppResult<Unit> = checkedWrite {
        transactions.inTransaction {
            val record = repository.investment(id) ?: throw IllegalArgumentException("理财台账不存在")
            repository.save(record.copy(closed = closed, updatedAt = time.nowMillis())).orRollback()
        }
    }
}

/**
 * 理财关联流水的改删守卫（在事务内调用，含还原）。
 *
 * - 投入/赎回必须是转账且方向正确，赎回累计不得超过投入累计；
 * - 收益必须是收入、损失必须是支出。
 */
class InvestmentTxnGuard(
    private val repository: InvestmentRepository,
    private val txns: TxnRepository,
) {
    companion object {
        const val LINK_INVALID = "investment_link_invalid"
        const val DIRECTION = "investment_direction"
        const val OVER_REDEEM = "investment_over_redeem"
    }

    suspend fun validate(updated: Txn): AppError? {
        val link = repository.linkForTxn(updated.id) ?: return null
        val record = repository.investment(link.investmentId) ?: return AppError.Validation(LINK_INVALID)
        if (updated.ledgerId != record.ledgerId) return AppError.Validation(LINK_INVALID)
        return when (link.kind) {
            InvestmentLinkKind.INVEST -> {
                if (updated.type != TransactionType.TRANSFER || updated.toAccountId != record.accountId) {
                    AppError.Validation(DIRECTION)
                } else {
                    overRedeem(record, updated)
                }
            }
            InvestmentLinkKind.REDEEM -> {
                if (updated.type != TransactionType.TRANSFER || updated.accountId != record.accountId) {
                    AppError.Validation(DIRECTION)
                } else {
                    overRedeem(record, updated)
                }
            }
            InvestmentLinkKind.INCOME ->
                if (updated.type != TransactionType.INCOME) AppError.Validation(DIRECTION) else null
            InvestmentLinkKind.EXPENSE ->
                if (updated.type != TransactionType.EXPENSE) AppError.Validation(DIRECTION) else null
        }
    }

    private suspend fun overRedeem(record: Investment, updated: Txn): AppError? {
        val links = repository.links(record.id)
        val entries = txns.listByLedger(record.ledgerId).associateBy { it.id }
        fun totalOf(kind: InvestmentLinkKind): Money = Money.sum(
            links.filter { it.kind == kind }.mapNotNull { link ->
                val txn = if (link.txnId == updated.id) updated else entries[link.txnId]
                txn?.takeIf { !it.isDeleted }?.amount
            },
        )
        val invested = totalOf(InvestmentLinkKind.INVEST)
        val redeemed = totalOf(InvestmentLinkKind.REDEEM)
        return if (redeemed > invested) AppError.Validation(OVER_REDEEM) else null
    }
}