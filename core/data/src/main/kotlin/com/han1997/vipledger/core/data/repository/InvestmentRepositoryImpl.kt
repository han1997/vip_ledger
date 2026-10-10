package com.han1997.vipledger.core.data.repository

import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.data.mapper.toEntity
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.domain.model.Investment
import com.han1997.vipledger.core.domain.model.InvestmentLink
import com.han1997.vipledger.core.domain.repository.InvestmentRepository
import kotlinx.coroutines.flow.map

class InvestmentRepositoryImpl(private val db: LedgerDatabase) : InvestmentRepository {
    private val dao = db.investmentDao()

    override fun observeInvestments(ledgerId: String) =
        dao.records(ledgerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeLinks(ledgerId: String) =
        dao.linksByLedger(ledgerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun investment(id: String) = dao.record(id)?.toDomain()

    override suspend fun links(investmentId: String) = dao.links(investmentId).map { it.toDomain() }

    override suspend fun linksForLedger(ledgerId: String) = dao.linksForLedger(ledgerId).map { it.toDomain() }

    override suspend fun linkForTxn(txnId: String) = dao.linkForTxn(txnId)?.toDomain()

    override suspend fun save(investment: Investment) = dataResult { dao.save(investment.toEntity()) }

    override suspend fun link(link: InvestmentLink) = dataResult { dao.link(link.toEntity()) }
}