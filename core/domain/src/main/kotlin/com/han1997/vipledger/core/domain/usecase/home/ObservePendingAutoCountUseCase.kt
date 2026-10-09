package com.han1997.vipledger.core.domain.usecase.home

import com.han1997.vipledger.core.domain.autobook.AutoBookRepository
import com.han1997.vipledger.core.domain.autobook.CandidateStatus
import kotlinx.coroutines.flow.map

class ObservePendingAutoCountUseCase(private val repository: AutoBookRepository) {
    operator fun invoke(ledgerId: String) = repository.observeCandidates(ledgerId).map { rows -> rows.count { it.status == CandidateStatus.PENDING } }
}
