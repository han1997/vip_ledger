package com.han1997.vipledger.core.domain.usecase.ledger

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.support.TransactionRunner

/** 设为默认账本（全局唯一）。已归档的账本先解除归档再设为默认。 */
class SetDefaultLedgerUseCase(
    private val ledgerRepository: LedgerRepository,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(ledgerId: String): AppResult<Unit> {
        val ledger = ledgerRepository.getById(ledgerId)
            ?: return AppResult.failure(AppError.NotFound("账本不存在"))
        if (ledger.isDefault) return AppResult.success(Unit)

        val now = timeProvider.nowMillis()
        return transactionRunner.inTransaction {
            if (ledger.isArchived) {
                ledgerRepository.setArchived(ledgerId, null)
            }
            ledgerRepository.setDefault(ledgerId, now)
        }
    }
}