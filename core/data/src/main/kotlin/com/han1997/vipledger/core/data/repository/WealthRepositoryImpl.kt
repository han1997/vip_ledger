package com.han1997.vipledger.core.data.repository

import androidx.room.withTransaction
import com.han1997.vipledger.core.data.support.dataResult
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.*
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.repository.WealthRepository
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class WealthRepositoryImpl(private val db: LedgerDatabase) : WealthRepository {
    private val dao = db.wealthDao()
    override fun observeGoals(ledgerId: String) = dao.goals(ledgerId).map { it.map(::domainGoal) }
    override fun observeLinks(ledgerId: String) = dao.linksByLedger(ledgerId).map { it.map(::domainLink) }
    override suspend fun goal(id: String) = dao.goal(id)?.let(::domainGoal)
    override suspend fun links(goalId: String) = dao.links(goalId).map(::domainLink)
    override suspend fun linkForTxn(txnId: String) = dao.linkForTxn(txnId)?.let(::domainLink)
    override suspend fun saveGoal(goal: SavingsGoal) = dataResult { dao.save(SavingsGoalEntity(goal.id, goal.ledgerId, goal.accountId, goal.name, goal.target.cents, goal.deadline?.toString(), goal.archived, goal.createdAt, goal.updatedAt)) }
    override suspend fun link(link: SavingsLink) = dataResult { dao.link(SavingsLinkEntity(link.txnId, link.goalId, link.createdAt)) }
    override fun observeCards(ledgerId: String) = dao.cards(ledgerId).map { rows -> rows.mapNotNull { row -> HomeCard.entries.find { it.name == row.cardKey }?.let { HomeCardSetting(it, row.position, row.visible) } } }
    override suspend fun saveCards(ledgerId: String, rows: List<HomeCardSetting>) = dataResult {
        db.withTransaction { dao.clearCards(ledgerId); dao.cards(rows.map { HomeCardEntity(ledgerId, it.card.name, it.position, it.visible) }) }
    }
    override fun observeBaselines(ledgerId: String) = dao.baselines(ledgerId).map { it.map { row -> AssetBaseline(row.accountId, row.ledgerId, row.capturedAt, Money.ofCents(row.balanceCents), row.included) } }
    override suspend fun ensureBaselines(accounts: List<Account>, now: Long) = dataResult {
        db.withTransaction {
            for ((ledgerId, rows) in accounts.groupBy { it.ledgerId }) {
                val existing = dao.baselineIds(ledgerId).toSet()
                val missing = rows.filter { it.id !in existing }
                if (missing.isNotEmpty()) dao.baselines(missing.map { AssetBaselineEntity(it.id, it.ledgerId, now, it.currentBalance.cents, it.isUsable && it.includeInNetWorth) })
            }
        }
    }
    private fun domainGoal(row: SavingsGoalEntity) = SavingsGoal(row.id, row.ledgerId, row.accountId, row.name, Money.ofCents(row.targetCents), row.deadline?.let(LocalDate::parse), row.archived, row.createdAt, row.updatedAt)
    private fun domainLink(row: SavingsLinkEntity) = SavingsLink(row.txnId, row.goalId, row.createdAt)
}
