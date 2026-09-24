package com.han1997.vipledger.core.domain.usecase.search

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.CategoryTree
import com.han1997.vipledger.core.domain.calculator.SearchQueryMapper
import com.han1997.vipledger.core.domain.model.SearchQuery
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * 组合搜索：把 [SearchQuery] 翻译成仓库过滤条件后订阅结果。
 *
 * 「选中的一级分类要带出它的二级分类」需要先读一次分类表，所以这里用 `flow { }` 包一层；
 * 分类表在搜索过程中变化极少，不额外订阅。
 */
class SearchTxnsUseCase(
    private val txnRepository: TxnRepository,
    private val categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider,
) {

    operator fun invoke(query: SearchQuery): Flow<List<Txn>> = flow {
        val today = timeProvider.nowMillis().toLocalDate(timeProvider.zone)
        val expanded = if (query.categoryIds.isEmpty()) {
            emptySet()
        } else {
            val categories = categoryRepository.listByLedger(query.ledgerId, includeArchived = true)
            CategoryTree.subtreeIds(query.categoryIds, categories)
        }
        emitAll(txnRepository.observe(SearchQueryMapper.toFilter(query, today, expanded)))
    }
}