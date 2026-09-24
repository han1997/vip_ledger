package com.han1997.vipledger.core.domain.usecase.tag

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.TagRepository

/**
 * 删除标签（软删）。历史账目上的标签关联保留在 `txn_tag` 表里：
 * 统计按标签聚合时读的是当时的关联，不会因为之后删标签而改变历史数字。
 */
class DeleteTagUseCase(
    private val tagRepository: TagRepository,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(tagId: String): AppResult<Unit> {
        tagRepository.getById(tagId) ?: return AppResult.failure(AppError.NotFound("标签不存在"))
        return tagRepository.softDelete(tagId, timeProvider.nowMillis())
    }
}