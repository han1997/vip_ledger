package com.han1997.vipledger.core.domain.usecase.txn

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.TransactionRunner

/** 删除账目：软删除进回收站，并同步受影响账户的余额。 */
class DeleteTxnUseCase(
    private val txnRepository: TxnRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
    private val savingsGuard: com.han1997.vipledger.core.domain.usecase.wealth.SavingsTxnGuard? = null,
) {

    suspend operator fun invoke(txnId: String): AppResult<Unit> {
        val existing = txnRepository.getById(txnId)
            ?: return AppResult.failure(AppError.NotFound("账目不存在"))
        if (existing.deletedAt != null) return AppResult.success(Unit)
        val now = timeProvider.nowMillis()
        return transactionRunner.inTransaction {
            savingsGuard?.validate(existing.copy(deletedAt = now))?.let { return@inTransaction AppResult.failure(it) }
            com.han1997.vipledger.core.domain.calculator.RefundValidator.validate(existing.copy(deletedAt = now), txnRepository.listByLedger(existing.ledgerId))
                ?.let { return@inTransaction AppResult.failure(it) }
            val result = txnRepository.softDelete(txnId, now)
            if (result.isSuccess) {
                balanceSyncer.syncBalances(existing.affectedAccountIds(), now)
            }
            result
        }
    }

    /** 回收站还原。 */
    suspend fun restore(txnId: String): AppResult<Unit> {
        val existing = txnRepository.getById(txnId)
            ?: return AppResult.failure(AppError.NotFound("账目不存在"))
        val now = timeProvider.nowMillis()
        return transactionRunner.inTransaction {
            savingsGuard?.validate(existing.copy(deletedAt = null))?.let { return@inTransaction AppResult.failure(it) }
            com.han1997.vipledger.core.domain.calculator.RefundValidator.validate(existing.copy(deletedAt = null), txnRepository.listByLedger(existing.ledgerId))
                ?.let { return@inTransaction AppResult.failure(it) }
            val result = txnRepository.restore(txnId, now)
            if (result.isSuccess) {
                balanceSyncer.syncBalances(existing.affectedAccountIds(), now)
            }
            result
        }
    }
}
