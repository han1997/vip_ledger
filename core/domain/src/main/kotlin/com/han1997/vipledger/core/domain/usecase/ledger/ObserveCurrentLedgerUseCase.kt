package com.han1997.vipledger.core.domain.usecase.ledger

import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.support.CurrentLedgerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 当前账本：用户显式选择优先；选择失效（被删除 / 被归档 / 不存在）时回退到默认账本，
 * 默认账本也没有时退回任意一个未归档账本。保证界面永远有一个可用的账本上下文。
 */
class ObserveCurrentLedgerUseCase(
    private val currentLedgerStore: CurrentLedgerStore,
    private val ledgerRepository: LedgerRepository,
) {

    operator fun invoke(): Flow<Ledger?> = combine(
        currentLedgerStore.observeLedgerId(),
        ledgerRepository.observeAll(includeArchived = true),
    ) { selectedId, ledgers ->
        ledgers.firstOrNull { it.id == selectedId && !it.isArchived }
            ?: ledgers.firstOrNull { it.isDefault && !it.isArchived }
            ?: ledgers.firstOrNull { !it.isArchived }
            ?: ledgers.firstOrNull()
    }
}