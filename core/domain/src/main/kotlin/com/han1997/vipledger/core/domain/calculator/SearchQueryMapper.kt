package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.SearchQuery
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.model.TransactionType
import kotlinx.datetime.LocalDate

/**
 * 搜索条件 → 仓库过滤条件（纯函数，方便直接单测）。
 *
 * [expandedCategoryIds] 是已经展开过子分类的分类 id 集合，由用例用
 * [CategoryTree.subtreeIds] 算好传进来（「选一级分类带出子分类」的口径）。
 */
object SearchQueryMapper {

    fun toFilter(
        query: SearchQuery,
        today: LocalDate,
        expandedCategoryIds: Set<String> = query.categoryIds,
    ): TxnFilter = TxnFilter(
        ledgerId = query.ledgerId,
        keyword = query.keyword.trim().takeIf { it.isNotEmpty() },
        range = query.resolveRange(today),
        categoryIds = expandedCategoryIds,
        tagIds = query.tagIds,
        accountIds = query.accountIds,
        types = query.types.ifEmpty { TransactionType.entries.toSet() },
        minAmount = query.minAmount,
        maxAmount = query.maxAmount,
        draftStatus = query.draftStatus,
    )
}