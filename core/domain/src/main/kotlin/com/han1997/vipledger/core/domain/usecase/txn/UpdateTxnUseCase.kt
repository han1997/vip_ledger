package com.han1997.vipledger.core.domain.usecase.txn

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.TransactionRunner

/** 修改账目。转出/转入账户变化时，新旧账户的余额都要重算。 */
class UpdateTxnUseCase(
    private val txnRepository: TxnRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(txn: Txn): AppResult<Txn> {
        val existing = txnRepository.getById(txn.id)
            ?: return AppResult.failure(AppError.NotFound("账目不存在或已删除"))
        if (existing.deletedAt != null) {
            return AppResult.failure(AppError.Validation("账目在回收站里，请先还原"))
        }
        val now = timeProvider.nowMillis()
        val updated = txn.copy(updatedAt = now)
        val accounts = accountRepository.listByLedger(updated.ledgerId, includeArchived = true)
        val categories = categoryRepository.listByLedger(updated.ledgerId, includeArchived = true)
        TxnValidator.validate(updated, accounts, categories)?.let { return AppResult.failure(it) }

        val affected = (existing.affectedAccountIds() + updated.affectedAccountIds()).distinct()
        return transactionRunner.inTransaction {
            com.han1997.vipledger.core.domain.calculator.RefundValidator.validate(updated, txnRepository.listByLedger(updated.ledgerId))
                ?.let { return@inTransaction AppResult.failure(it) }
            val result = txnRepository.update(updated)
            if (result.isSuccess) {
                balanceSyncer.syncBalances(affected, now)
            }
            result
        }.map { updated }
    }
}
