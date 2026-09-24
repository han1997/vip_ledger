package com.han1997.vipledger.core.domain.usecase.ledger

import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow

/** 账本列表订阅（账本切换器用）。 */
class ObserveLedgersUseCase(private val ledgerRepository: LedgerRepository) {
    operator fun invoke(includeArchived: Boolean = false): Flow<List<Ledger>> =
        ledgerRepository.observeAll(includeArchived)
}