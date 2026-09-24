package com.han1997.vipledger.core.domain.usecase.category

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.CategoryTree
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.TransactionRunner

/**
 * 删除分类（软删，一级分类会连带软删其子分类）。
 *
 * 被账目引用时拒绝删除：分类是账目的展示与统计维度，删掉会让历史数据出现「未知分类」。
 */
class DeleteCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val txnRepository: TxnRepository,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
) {

    /** 返回被删除的分类数量（含级联的子分类）。 */
    suspend operator fun invoke(categoryId: String): AppResult<Int> {
        val category = categoryRepository.getById(categoryId)
            ?: return AppResult.failure(AppError.NotFound("分类不存在"))
        val all = categoryRepository.listByLedger(category.ledgerId, includeArchived = true)
        val targets = listOf(category) + CategoryTree.childrenOf(categoryId, all)

        val used = targets.firstOrNull { txnRepository.countByCategory(it.id) > 0 }
        if (used != null) {
            return AppResult.failure(AppError.Validation("分类「" + used.name + "」已被账目使用，不能删除"))
        }

        val now = timeProvider.nowMillis()
        return transactionRunner.inTransaction<AppResult<Int>> {
            targets.forEach { target ->
                val result = categoryRepository.softDelete(target.id, now)
                if (result is AppResult.Failure) return@inTransaction result
            }
            AppResult.success(targets.size)
        }
    }
}