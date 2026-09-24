package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.model.Category

/**
 * 两级分类的约束与树上操作（docs/DATA-MODEL.md 3.3）。
 *
 * 层级用 `parent_id` 自关联表达，深度强约束为 2：一级分类的 parent 为空，
 * 二级分类的父必须是一级分类，且父子同账本、同 kind。
 */
object CategoryTree {

    const val MAX_DEPTH = 2

    /** 校验失败返回 [AppError.Validation]，由用例转成 AppResult。 */
    fun validate(candidate: Category, existing: List<Category>): AppError? {
        if (candidate.name.isBlank()) {
            return AppError.Validation("分类名不能为空")
        }
        val siblings = existing.filter { it.id != candidate.id && it.ledgerId == candidate.ledgerId }
        val duplicated = siblings.any { it.parentId == candidate.parentId && it.kind == candidate.kind && it.name == candidate.name }
        if (duplicated) {
            return AppError.Validation("同级下已存在同名分类：" + candidate.name)
        }
        val parentId = candidate.parentId ?: return null
        val parent = existing.firstOrNull { it.id == parentId }
            ?: return AppError.Validation("父分类不存在")
        if (parent.ledgerId != candidate.ledgerId) {
            return AppError.Validation("父分类不属于当前账本")
        }
        if (parent.kind != candidate.kind) {
            return AppError.Validation("父分类的收支方向与子分类不一致")
        }
        if (parent.parentId != null) {
            return AppError.Validation("分类最多两级，不能挂在二级分类下")
        }
        return null
    }

    fun childrenOf(parentId: String, all: List<Category>): List<Category> =
        all.filter { it.parentId == parentId && it.deletedAt == null }.sortedBy { it.sortOrder }

    /** 一级分类。 */
    fun topLevel(all: List<Category>): List<Category> = all.filter { it.isTopLevel && it.deletedAt == null }

    fun topLevelOf(categoryId: String, all: List<Category>): Category? {
        val category = all.firstOrNull { it.id == categoryId } ?: return null
        val parentId = category.parentId ?: return category
        return all.firstOrNull { it.id == parentId } ?: category
    }

    /** 展开成「自身 + 全部子分类」的 id 集合，用于搜索时按一级分类带出子分类。 */
    fun subtreeIds(roots: Collection<String>, all: List<Category>): Set<String> {
        val result = LinkedHashSet<String>()
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!result.add(id)) continue
            all.filter { it.parentId == id }.forEach { queue.addLast(it.id) }
        }
        return result
    }
}