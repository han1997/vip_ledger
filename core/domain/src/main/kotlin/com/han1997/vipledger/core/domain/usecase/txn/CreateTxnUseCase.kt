package com.han1997.vipledger.core.domain.usecase.txn

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner

/** 记一笔。校验、写流水、刷新账户余额缓存都在一个事务里完成。 */
class CreateTxnUseCase(
    private val txnRepository: TxnRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(draft: TxnDraft): AppResult<Txn> {
        val now = timeProvider.nowMillis()
        val txn = draft.toTxn(idGenerator.newId(), now)
        val accounts = accountRepository.listByLedger(draft.ledgerId, includeArchived = true)
        val categories = categoryRepository.listByLedger(draft.ledgerId, includeArchived = true)
        TxnValidator.validate(txn, accounts, categories)?.let { return AppResult.failure(it) }

        return transactionRunner.inTransaction {
            val result = txnRepository.insert(txn)
            if (result.isSuccess) {
                balanceSyncer.syncBalances(txn.affectedAccountIds(), now)
            }
            result
        }.map { txn }
    }
}

internal fun TxnDraft.toTxn(id: String, now: Long): Txn = Txn(
    id = id,
    ledgerId = ledgerId,
    type = type,
    amount = amount,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    occurredAt = occurredAt ?: now,
    title = title,
    remark = remark,
    source = source,
    draftStatus = draftStatus,
    excludeFromStats = excludeFromStats,
    recurringRuleId = recurringRuleId,
    installmentPlanId = installmentPlanId,
    installmentPeriodNo = installmentPeriodNo,
    importHash = importHash,
    tagIds = tagIds.distinct(),
    createdAt = now,
    updatedAt = now,
)

internal fun Txn.affectedAccountIds(): List<String> = listOfNotNull(accountId, toAccountId).distinct()