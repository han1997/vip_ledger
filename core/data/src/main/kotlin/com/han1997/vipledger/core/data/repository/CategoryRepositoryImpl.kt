package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.CategoryDao
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun observeByLedger(ledgerId: String, includeArchived: Boolean): Flow<List<Category>> =
        categoryDao.observeByLedger(ledgerId, includeArchived).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listByLedger(ledgerId: String, includeArchived: Boolean): List<Category> =
        categoryDao.listByLedger(ledgerId, includeArchived).map { it.toDomain() }

    override suspend fun getById(id: String): Category? = categoryDao.getById(id)?.toDomain()

    override suspend fun upsert(category: Category): AppResult<Unit> =
        dataResult { categoryDao.upsert(category.toEntity()) }

    override suspend fun upsertAll(categories: List<Category>): AppResult<Unit> =
        dataResult { categoryDao.upsertAll(categories.map { it.toEntity() }) }

    override suspend fun countChildren(parentId: String): Int = categoryDao.countChildren(parentId)

    override suspend fun softDelete(categoryId: String, deletedAt: Long): AppResult<Unit> =
        dataResult { categoryDao.softDelete(categoryId, deletedAt) }
}