package com.han1997.vipledger.core.domain.usecase.txn

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.model.DraftStatus

/** 确认待确认草稿：置为 `CONFIRMED` 并重算受影响账户余额（草稿此前不参与余额）。 */
class ConfirmDraftUseCase(
    private val txnRepository: TxnRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(txnId: String): AppResult<Txn> {
        val txn = txnRepository.getById(txnId)
            ?: return AppResult.failure(AppError.NotFound("账目不存在"))
        if (txn.deletedAt != null) return AppResult.failure(AppError.NotFound("账目已删除"))
        if (txn.draftStatus != DraftStatus.PENDING) {
            return AppResult.failure(AppError.Validation("只有待确认的账目才能确认"))
        }
        val now = timeProvider.nowMillis()
        val updated = txn.copy(draftStatus = DraftStatus.CONFIRMED, updatedAt = now)
        return transactionRunner.inTransaction {
            val result = txnRepository.updateDraftStatus(txnId, DraftStatus.CONFIRMED, now)
            if (result.isSuccess) {
                balanceSyncer.syncBalances(listOfNotNull(txn.accountId, txn.toAccountId).distinct(), now)
            }
            result
        }.map { updated }
    }
}

/** 丢弃待确认草稿：标记 `DISCARDED` 并软删，保留审计痕迹但不进任何列表与统计。 */
class DiscardDraftUseCase(
    private val txnRepository: TxnRepository,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(txnId: String): AppResult<Unit> {
        val txn = txnRepository.getById(txnId)
            ?: return AppResult.failure(AppError.NotFound("账目不存在"))
        if (txn.draftStatus != DraftStatus.PENDING) {
            return AppResult.failure(AppError.Validation("只有待确认的账目才能丢弃"))
        }
        val now = timeProvider.nowMillis()
        return transactionRunner.inTransaction {
            val result = txnRepository.updateDraftStatus(txnId, DraftStatus.DISCARDED, now)
            if (result.isSuccess) txnRepository.softDelete(txnId, now) else result
        }
    }
}
