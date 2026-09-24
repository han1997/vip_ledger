package com.han1997.vipledger.core.domain.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeByLedger(ledgerId: String, includeArchived: Boolean = false): Flow<List<Category>>

    suspend fun listByLedger(ledgerId: String, includeArchived: Boolean = false): List<Category>

    suspend fun getById(id: String): Category?

    suspend fun upsert(category: Category): AppResult<Unit>

    /** 批量写入（初始化内置分类用），必须在一个事务里完成。 */
    suspend fun upsertAll(categories: List<Category>): AppResult<Unit>

    suspend fun countChildren(parentId: String): Int

    suspend fun softDelete(categoryId: String, deletedAt: Long): AppResult<Unit>
}