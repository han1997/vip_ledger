package com.han1997.vipledger.core.domain.usecase.category

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.CategoryTree
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.model.CategoryKind

/**
 * 新建（[categoryId] 为 null）或编辑分类。
 *
 * 两层结构与「同级不重名、父子同账本同 kind、不能挂到二级分类下」的约束
 * 全部委托给 [CategoryTree.validate]，用例只负责拼装与错误转译。
 */
class SaveCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(
        categoryId: String?,
        ledgerId: String,
        parentId: String?,
        name: String,
        kind: CategoryKind,
        iconKey: String? = null,
        colorArgb: Int? = null,
    ): AppResult<Category> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AppResult.failure(AppError.Validation("分类名不能为空"))
        }

        val all = categoryRepository.listByLedger(ledgerId, includeArchived = true)
        val existing = categoryId?.let { id -> all.firstOrNull { it.id == id } }
        if (categoryId != null && existing == null) {
            return AppResult.failure(AppError.NotFound("分类不存在"))
        }
        val parent = parentId?.let { id -> all.firstOrNull { it.id == id } }
        if (parentId != null && parent == null) {
            return AppResult.failure(AppError.Validation("父分类不存在"))
        }
        if (parent != null && (parent.isArchived || parent.deletedAt != null)) {
            return AppResult.failure(AppError.Validation("父分类已归档，不能在其下新建分类"))
        }

        val now = timeProvider.nowMillis()
        val candidate = if (existing == null) {
            Category(
                id = idGenerator.newId(),
                ledgerId = ledgerId,
                parentId = parentId,
                name = trimmed,
                kind = kind,
                iconKey = iconKey,
                colorArgb = colorArgb,
                sortOrder = siblingCount(all, parentId),
                createdAt = now,
                updatedAt = now,
            )
        } else {
            existing.copy(
                parentId = parentId,
                name = trimmed,
                kind = kind,
                iconKey = iconKey ?: existing.iconKey,
                colorArgb = colorArgb ?: existing.colorArgb,
                updatedAt = now,
            )
        }

        CategoryTree.validate(candidate, all)?.let { return AppResult.failure(it) }
        return categoryRepository.upsert(candidate).map { candidate }
    }

    private fun siblingCount(all: List<Category>, parentId: String?): Int =
        all.count { it.parentId == parentId && it.deletedAt == null }
}