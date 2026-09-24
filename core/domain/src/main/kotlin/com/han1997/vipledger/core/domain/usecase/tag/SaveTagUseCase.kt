package com.han1997.vipledger.core.domain.usecase.tag

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.repository.TagRepository
import com.han1997.vipledger.core.domain.support.IdGenerator

/** 新建（[tagId] 为 null）或改名标签；同一账本内标签名唯一。 */
class SaveTagUseCase(
    private val tagRepository: TagRepository,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(
        tagId: String?,
        ledgerId: String,
        name: String,
        colorArgb: Int? = null,
    ): AppResult<Tag> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AppResult.failure(AppError.Validation("标签名不能为空"))
        }
        val all = tagRepository.listByLedger(ledgerId)
        if (all.any { it.id != tagId && it.name == trimmed }) {
            return AppResult.failure(AppError.Validation("已存在同名标签：" + trimmed))
        }

        val existing = tagId?.let { id -> all.firstOrNull { it.id == id } }
        if (tagId != null && existing == null) {
            return AppResult.failure(AppError.NotFound("标签不存在"))
        }

        val now = timeProvider.nowMillis()
        val tag = if (existing == null) {
            Tag(
                id = idGenerator.newId(),
                ledgerId = ledgerId,
                name = trimmed,
                colorArgb = colorArgb,
                sortOrder = all.size,
                createdAt = now,
                updatedAt = now,
            )
        } else {
            existing.copy(
                name = trimmed,
                colorArgb = colorArgb ?: existing.colorArgb,
                updatedAt = now,
            )
        }
        return tagRepository.upsert(tag).map { tag }
    }
}