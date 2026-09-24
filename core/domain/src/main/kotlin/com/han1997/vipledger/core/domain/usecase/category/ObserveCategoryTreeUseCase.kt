package com.han1997.vipledger.core.domain.usecase.category

import com.han1997.vipledger.core.domain.calculator.CategoryTree
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 两级分类树订阅：一级分类 + 其直接子分类。 */
class ObserveCategoryTreeUseCase(private val categoryRepository: CategoryRepository) {

    operator fun invoke(ledgerId: String, includeArchived: Boolean = false): Flow<List<CategoryNode>> =
        categoryRepository.observeByLedger(ledgerId, includeArchived)
            .map { CategoryTree.nodesOf(it, includeArchived) }
}