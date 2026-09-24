package com.han1997.vipledger.core.domain.usecase.ledger

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.support.CurrentLedgerStore

/** 切换当前账本（只影响界面上下文与后续记账的归属，不改动已有数据）。 */
class SelectLedgerUseCase(
    private val currentLedgerStore: CurrentLedgerStore,
    private val ledgerRepository: LedgerRepository,
) {

    suspend operator fun invoke(ledgerId: String): AppResult<Unit> {
        val ledger = ledgerRepository.getById(ledgerId)
            ?: return AppResult.failure(AppError.NotFound("账本不存在"))
        if (ledger.isArchived) {
            return AppResult.failure(AppError.Validation("已归档的账本不能切换为当前账本"))
        }
        currentLedgerStore.setLedgerId(ledgerId)
        return AppResult.success(Unit)
    }
}