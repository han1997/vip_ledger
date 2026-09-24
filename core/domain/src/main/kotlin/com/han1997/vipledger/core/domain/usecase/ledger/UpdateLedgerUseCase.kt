package com.han1997.vipledger.core.domain.usecase.ledger

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.repository.LedgerRepository

/** 改账本名与图标；账本之间必须重名唯一，否则账本切换器无法分辨。 */
class UpdateLedgerUseCase(
    private val ledgerRepository: LedgerRepository,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(ledgerId: String, name: String, iconKey: String? = null): AppResult<Ledger> {
        val ledger = ledgerRepository.getById(ledgerId)
            ?: return AppResult.failure(AppError.NotFound("账本不存在"))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AppResult.failure(AppError.Validation("账本名不能为空"))
        }
        val duplicated = ledgerRepository.listAll(includeArchived = true)
            .any { it.id != ledgerId && it.name == trimmed }
        if (duplicated) {
            return AppResult.failure(AppError.Validation("已存在同名账本：" + trimmed))
        }

        val updated = ledger.copy(
            name = trimmed,
            iconKey = iconKey ?: ledger.iconKey,
            updatedAt = timeProvider.nowMillis(),
        )
        return ledgerRepository.upsert(updated).map { updated }
    }
}