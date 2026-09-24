package com.han1997.vipledger.core.domain.usecase.tag

import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow

/** 标签列表订阅（账本内）。 */
class ObserveTagsUseCase(private val tagRepository: TagRepository) {
    operator fun invoke(ledgerId: String): Flow<List<Tag>> = tagRepository.observeByLedger(ledgerId)
}