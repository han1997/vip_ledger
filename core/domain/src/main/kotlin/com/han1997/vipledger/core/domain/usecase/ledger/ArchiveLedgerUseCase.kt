package com.han1997.vipledger.core.domain.usecase.ledger

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.LedgerRepository

/**
 * 归档 / 解除归档账本。归档是「收起来暂时不用」，数据保留；
 * 默认账本不能归档，且必须至少留一个未归档账本。
 */
class ArchiveLedgerUseCase(
    private val ledgerRepository: LedgerRepository,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(ledgerId: String, archived: Boolean): AppResult<Unit> {
        val ledger = ledgerRepository.getById(ledgerId)
            ?: return AppResult.failure(AppError.NotFound("账本不存在"))
        if (ledger.isArchived == archived) return AppResult.success(Unit)

        if (archived) {
            if (ledger.isDefault) {
                return AppResult.failure(AppError.Validation("默认账本不能归档，请先把其他账本设为默认"))
            }
            val active = ledgerRepository.listAll(includeArchived = false)
            if (active.size <= 1) {
                return AppResult.failure(AppError.Validation("至少要保留一个未归档账本"))
            }
        }

        val now = timeProvider.nowMillis()
        return ledgerRepository.setArchived(ledgerId, if (archived) now else null)
    }
}