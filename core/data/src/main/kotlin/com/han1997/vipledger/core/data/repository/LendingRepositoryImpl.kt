package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.LendingEntity
import com.han1997.vipledger.core.database.entity.LendingLinkEntity
import com.han1997.vipledger.core.domain.model.LendingLink
import com.han1997.vipledger.core.domain.model.LendingRecord
import com.han1997.vipledger.core.domain.repository.LendingRepository
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class LendingRepositoryImpl(private val db: LedgerDatabase) : LendingRepository {
    private val dao = db.lendingDao()

    override fun observeRecords(ledgerId: String) = dao.records(ledgerId).map { rows -> rows.map(::domainRecord) }

    override fun observeLinks(ledgerId: String) = dao.linksByLedger(ledgerId).map { rows -> rows.map(::domainLink) }

    override suspend fun record(id: String) = dao.record(id)?.let(::domainRecord)

    override suspend fun links(lendingId: String) = dao.links(lendingId).map(::domainLink)

    override suspend fun linkForTxn(txnId: String) = dao.linkForTxn(txnId)?.let(::domainLink)

    override suspend fun save(record: LendingRecord) = dataResult { dao.save(record.toEntity()) }

    override suspend fun link(link: LendingLink) = dataResult {
        dao.link(LendingLinkEntity(link.txnId, link.lendingId, link.kind, link.createdAt))
    }

    private fun domainRecord(row: LendingEntity) = LendingRecord(
        id = row.id,
        ledgerId = row.ledgerId,
        direction = row.direction,
        counterparty = row.counterparty,
        principal = Money.ofCents(row.principalCents),
        accountId = row.accountId,
        dueDate = row.dueDate?.let(LocalDate::parse),
        remark = row.remark,
        closed = row.closed,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun domainLink(row: LendingLinkEntity) = LendingLink(row.txnId, row.lendingId, row.kind, row.createdAt)

    private fun LendingRecord.toEntity() = LendingEntity(id, ledgerId, direction, counterparty, principal.cents, accountId, dueDate?.toString(), remark, closed, createdAt, updatedAt)
}
