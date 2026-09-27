package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.dao.TxnDao
import com.han1997.vipledger.core.database.dao.TxnTagDao
import com.han1997.vipledger.core.database.entity.TxnTagEntity
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.time.toEpochMillisBounds
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/**
 * 集合型过滤条件以 `,a,b,` 的 CSV 传给 SQL（`TxnDao.TXN_FILTER_SQL` 用 INSTR 判断）：
 * 既保住「空集合 = 不过滤」的语义，也不会踩 `IN ()` 的语法坑。时间区间在这里
 * 按设备时区折成 `[from, to)` 毫秒。
 */
internal object TxnFilterSql {

    /** 不传 LIMIT 时 SQLite 用 -1 表示「不限制」。 */
    const val NO_LIMIT: Int = -1

    fun csv(values: Collection<String>): String? =
        if (values.isEmpty()) null else values.joinToString(separator = ",", prefix = ",", postfix = ",")

    fun of(filter: TxnFilter, zone: TimeZone): FilterArgs {
        val bounds = filter.range?.toEpochMillisBounds(zone)
        return FilterArgs(
            from = bounds?.startInclusive,
            to = bounds?.endExclusive,
            keyword = filter.keyword?.takeIf { it.isNotBlank() },
            typeCsv = csv(filter.types.map(TransactionType::name)),
            categoryCsv = csv(filter.categoryIds),
            accountCsv = csv(filter.accountIds),
            tagCsv = csv(filter.tagIds),
            minAmountCents = filter.minAmount?.cents,
            maxAmountCents = filter.maxAmount?.cents,
            draftStatus = filter.draftStatus?.name,
            limit = filter.limit ?: NO_LIMIT,
        )
    }

    data class FilterArgs(
        val from: Long?,
        val to: Long?,
        val keyword: String?,
        val typeCsv: String?,
        val categoryCsv: String?,
        val accountCsv: String?,
        val tagCsv: String?,
        val minAmountCents: Long?,
        val maxAmountCents: Long?,
        val draftStatus: String?,
        val limit: Int,
    )
}

class TxnRepositoryImpl @Inject constructor(
    private val txnDao: TxnDao,
    private val txnTagDao: TxnTagDao,
    private val timeProvider: TimeProvider,
) : TxnRepository {

    override fun observe(filter: TxnFilter): Flow<List<Txn>> {
        val args = TxnFilterSql.of(filter, timeProvider.zone)
        return txnDao.observeFiltered(
            ledgerId = filter.ledgerId,
            includeDeleted = filter.includeDeleted,
            from = args.from,
            to = args.to,
            keyword = args.keyword,
            typeCsv = args.typeCsv,
            categoryCsv = args.categoryCsv,
            accountCsv = args.accountCsv,
            tagCsv = args.tagCsv,
            minAmount = args.minAmountCents,
            maxAmount = args.maxAmountCents,
            draftStatus = args.draftStatus,
            limit = args.limit,
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun list(filter: TxnFilter): List<Txn> {
        val args = TxnFilterSql.of(filter, timeProvider.zone)
        return txnDao.listFiltered(
            ledgerId = filter.ledgerId,
            includeDeleted = filter.includeDeleted,
            from = args.from,
            to = args.to,
            keyword = args.keyword,
            typeCsv = args.typeCsv,
            categoryCsv = args.categoryCsv,
            accountCsv = args.accountCsv,
            tagCsv = args.tagCsv,
            minAmount = args.minAmountCents,
            maxAmount = args.maxAmountCents,
            draftStatus = args.draftStatus,
            limit = args.limit,
        ).map { it.toDomain() }
    }

    override suspend fun getById(id: String): Txn? = txnDao.getWithTags(id)?.toDomain()

    override suspend fun listByAccount(accountId: String, includeDeleted: Boolean): List<Txn> =
        txnDao.listByAccount(accountId, includeDeleted).map { it.toDomain() }

    override suspend fun listByLedger(ledgerId: String, includeDeleted: Boolean): List<Txn> =
        txnDao.listByLedger(ledgerId, includeDeleted).map { it.toDomain() }

    override suspend fun insert(txn: Txn): AppResult<Unit> = dataResult { writeWithTags(txn) }

    override suspend fun update(txn: Txn): AppResult<Unit> = dataResult { writeWithTags(txn) }

    override suspend fun updateDraftStatus(
        id: String,
        status: DraftStatus,
        updatedAt: Long,
    ): AppResult<Unit> = dataResult { txnDao.updateDraftStatus(id, status.name, updatedAt) }

    override suspend fun softDelete(id: String, deletedAt: Long): AppResult<Unit> =
        dataResult { txnDao.softDelete(id, deletedAt) }

    override suspend fun restore(id: String, updatedAt: Long): AppResult<Unit> =
        dataResult { txnDao.restore(id, updatedAt) }

    override suspend fun existsImportHash(hash: String): Boolean = txnDao.existsImportHash(hash)

    override suspend fun countByLedger(ledgerId: String): Int = txnDao.countByLedger(ledgerId)

    override suspend fun countByCategory(categoryId: String): Int = txnDao.countByCategory(categoryId)

    private suspend fun writeWithTags(txn: Txn) {
        txnDao.upsert(txn.toEntity())
        txnTagDao.deleteByTxnId(txn.id)
        val links = txn.tagIds.distinct().map { TxnTagEntity(txnId = txn.id, tagId = it) }
        if (links.isNotEmpty()) txnTagDao.insertAll(links)
    }
}
