package com.han1997.vipledger.core.domain.usecase.recurring

import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** 待确认草稿（周期规则 / 以后的 AI 记账都往里放）。 */
class ObservePendingDraftsUseCase(private val txnRepository: TxnRepository) {
    operator fun invoke(ledgerId: String): Flow<List<Txn>> = txnRepository.observe(
        TxnFilter(
            ledgerId = ledgerId,
            types = TransactionType.entries.toSet(),
            draftStatus = DraftStatus.PENDING,
        ),
    )
}
