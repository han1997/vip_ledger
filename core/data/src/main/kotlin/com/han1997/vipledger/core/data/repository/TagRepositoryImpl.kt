package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.TagDao
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.repository.TagRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
) : TagRepository {

    override fun observeByLedger(ledgerId: String): Flow<List<Tag>> =
        tagDao.observeByLedger(ledgerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listByLedger(ledgerId: String): List<Tag> = tagDao.listByLedger(ledgerId).map { it.toDomain() }

    /** SQL 里 `IN ()` 是非法语法，空集合直接短路。 */
    override suspend fun listByIds(ids: Collection<String>): List<Tag> =
        if (ids.isEmpty()) emptyList() else tagDao.listByIds(ids.toList()).map { it.toDomain() }

    override suspend fun getById(id: String): Tag? = tagDao.getById(id)?.toDomain()

    override suspend fun upsert(tag: Tag): AppResult<Unit> = dataResult { tagDao.upsert(tag.toEntity()) }

    override suspend fun softDelete(tagId: String, deletedAt: Long): AppResult<Unit> =
        dataResult { tagDao.softDelete(tagId, deletedAt) }
}