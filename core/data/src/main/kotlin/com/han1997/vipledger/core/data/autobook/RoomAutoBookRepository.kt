package com.han1997.vipledger.core.data.autobook

import androidx.room.withTransaction
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AutoEvidenceEntity
import com.han1997.vipledger.core.domain.autobook.*
import com.han1997.vipledger.core.domain.calculator.TxnValidator
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.nlp.CategoryClassifier
import com.han1997.vipledger.core.domain.nlp.MerchantRuleMatcher
import com.han1997.vipledger.core.domain.nlp.ranked
import com.han1997.vipledger.core.domain.repository.*
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.model.*
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAutoBookRepository @Inject constructor(
    private val database: LedgerDatabase,
    private val accounts: AccountRepository,
    private val ledgers: LedgerRepository,
    private val categories: CategoryRepository,
    private val txns: TxnRepository,
    private val rules: MerchantRuleRepository,
    private val classifier: CategoryClassifier,
    private val balances: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
) : AutoBookRepository {
    private val dao get() = database.autoBookDao()
    override fun observeConfig() = dao.observeConfig().map { it.domain() }
    override suspend fun config() = dao.config().domain()
    override suspend fun saveConfig(config: AutoBookConfig) = guarded {
        if (config.enabled) require(ledgers.getById(config.ledgerId)?.let { !it.isArchived && it.currencyCode == "CNY" } == true)
        dao.saveConfig(config.entity())
    }
    override fun observeBindings(ledgerId: String) = dao.observeBindings(ledgerId).map { rows -> rows.map { it.domain() } }
    override suspend fun bind(binding: AccountBinding) = guarded {
        require(binding.token.isNotBlank())
        require(accounts.getById(binding.accountId)?.let { it.ledgerId == binding.ledgerId && it.isUsable } == true)
        dao.bind(binding.entity())
    }
    override suspend fun removeBinding(binding: AccountBinding) = guarded { dao.unbind(binding.entity()) }
    override fun observeCandidates(ledgerId: String) = dao.observeCandidates(ledgerId).map { rows -> rows.map { it.domain() } }

    override suspend fun capture(observation: PaymentObservation): AppResult<AutoCandidate?> = guarded {
        val facts = PaymentParser.parse(observation) ?: return@guarded null
        val prepared = database.withTransaction {
            val config = config()
            if (!config.enabled || observation.app !in config.enabledApps) return@withTransaction null
            if (ledgers.getById(config.ledgerId)?.let { !it.isArchived && it.currencyCode == "CNY" } != true) return@withTransaction null
            val externalKey = facts.externalId?.let { externalKey(observation.app, facts.type, it) }
            val channel = if (observation.source == CaptureSource.NOTIFICATION) "notification" else "page"
            val eventKey = "event:${observation.app}:$channel:${hash(observation.eventKey)}:${facts.externalId.orEmpty()}"
            // A bill may initially hide its order number, or only say "transaction successful"
            // without direction. Enrich that same observation instead of creating another row.
            val directionlessId = if (facts.type == TransactionType.EXPENSE) {
                facts.externalId?.let { dao.evidence(config.ledgerId, externalKey(observation.app, null, it)) }
            } else null
            val incompleteId = if (facts.externalId != null) {
                dao.evidence(config.ledgerId, "event:${observation.app}:$channel:${hash(observation.eventKey)}:")
                    ?.let { dao.candidate(it)?.domain() }?.takeIf {
                        it.status == CandidateStatus.PENDING && it.facts.externalId == null &&
                            facts.amountCents != null && it.facts.amountCents == facts.amountCents &&
                            facts.paidAt != null && it.facts.paidAt == facts.paidAt &&
                            (it.facts.type == null || it.facts.type == facts.type)
                    }?.id
            } else null
            val knownId = externalKey?.let { dao.evidence(config.ledgerId, it) }
                ?: directionlessId ?: dao.evidence(config.ledgerId, eventKey) ?: incompleteId
            val known = knownId?.let { dao.candidate(it)?.domain() }
            if (known != null && known.status != CandidateStatus.PENDING) {
                remember(config.ledgerId, eventKey, known.id)
                return@withTransaction null
            }
            // A later incomplete page must not erase a better pending observation.
            if (known != null && known.facts.success && (!facts.success || facts.amountCents == null)) return@withTransaction null
            val bindings = dao.bindings(config.ledgerId)
            val usable = accounts.listByLedger(config.ledgerId).filter { it.isUsable }
            fun match(token: String?): String? = bindings.singleOrNull {
                it.app == observation.app.name && it.token == token && usable.any { a -> a.id == it.accountId }
            }?.accountId
            val accountId = match(facts.accountToken)
            val targetId = match(facts.targetToken)
            val original = facts.originalId?.let { originalId ->
                dao.evidence(config.ledgerId, externalKey(observation.app, TransactionType.EXPENSE, originalId))
                    ?.let { dao.candidate(it)?.txnId }?.let { txns.getById(it) }
            }
            // Amount-only candidates are intentionally held rather than silently merged or dropped.
            val near = dao.nearby(config.ledgerId, observation.observedAt - 120_000, observation.observedAt + 120_000)
                .map { it.domain() }.firstOrNull {
                    it.id != knownId && facts.amountCents != null && it.facts.amountCents == facts.amountCents &&
                        it.facts.type == facts.type &&
                        (it.facts.merchant == facts.merchant || it.facts.merchant.isBlank() || facts.merchant.isBlank())
                }
            // P6 判重共享：同账本同日（±1 天容差）内同额、方向一致的 AI 页来源账目（截图草稿/已确认）
            // 也视为疑似重复 → 候选标 DUPLICATE 待确认，自动入账被挡住，交人工处理。
            val nearAmount = facts.amountCents
            val nearAi = if (nearAmount != null) {
                val zone = timeProvider.zone
                val dayStart = observation.observedAt.toLocalDate(zone).startOfDayEpochMillis(zone)
                database.txnDao().nearAiSourceEntry(
                    config.ledgerId, dayStart - DAY_MILLIS, dayStart + DAY_MILLIS,
                    nearAmount, facts.type?.name,
                )
            } else null
            val paidAt = facts.paidAt
            val reason = when {
                facts.reason != ReviewReason.NONE -> facts.reason
                paidAt != null && (paidAt < observation.observedAt - 600_000 || paidAt > observation.observedAt + 300_000) -> ReviewReason.TIME
                accountId == null || (facts.type?.requiresTargetAccount == true && (targetId == null || targetId == accountId)) -> ReviewReason.ACCOUNT
                facts.type == TransactionType.REFUND && (original == null || !refundAllowed(original, facts.amountCents ?: 0)) -> ReviewReason.REFUND
                near != null || nearAi != null -> ReviewReason.DUPLICATE
                observation.app !in config.verifiedApps -> ReviewReason.UNVERIFIED
                else -> ReviewReason.NONE
            }
            val candidate = AutoCandidate(knownId ?: UUID.randomUUID().toString(), config.ledgerId, observation.app,
                observation.source, facts.paidAt ?: observation.observedAt, facts, accountId, targetId, reason = reason,
                refundOfTxnId = original?.id, duplicateTxnId = near?.txnId ?: nearAi?.id)
            dao.saveCandidate(candidate.entity())
            remember(config.ledgerId, eventKey, candidate.id)
            externalKey?.let { remember(config.ledgerId, it, candidate.id) }
            candidate
        }
        if (prepared == null || prepared.reason != ReviewReason.NONE) return@guarded prepared
        // Persist the candidate before attempting the atomic financial mutation. A failed write
        // remains reviewable and can be retried after a process restart.
        val result = confirm(prepared.id, CandidateConfirmation(requireNotNull(facts.type), requireNotNull(facts.amountCents),
            requireNotNull(prepared.accountId), prepared.targetAccountId, facts.merchant, prepared.refundOfTxnId))
        database.withTransaction {
            val current = requireNotNull(dao.candidate(prepared.id)?.domain())
            if (result is AppResult.Failure && current.status == CandidateStatus.PENDING) {
                current.copy(reason = ReviewReason.STORAGE).also { dao.saveCandidate(it.entity()) }
            } else if (result is AppResult.Success) current else null
        }
    }

    override suspend fun confirm(id: String, input: CandidateConfirmation): AppResult<Txn> = guarded {
        database.withTransaction {
            val candidate = requireNotNull(dao.candidate(id)?.domain())
            require(candidate.status == CandidateStatus.PENDING)
            post(candidate, input)
        }
    }

    private suspend fun post(candidate: AutoCandidate, input: CandidateConfirmation): Txn {
        require(input.type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER, TransactionType.REFUND))
        require(ledgers.getById(candidate.ledgerId)?.let { !it.isArchived && it.currencyCode == "CNY" } == true)
        val usable = accounts.listByLedger(candidate.ledgerId).filter { it.isUsable }
        val original = input.refundOfTxnId?.let { requireNotNull(txns.getById(it)) }
        if (original != null) {
            require(input.type == TransactionType.REFUND && original.ledgerId == candidate.ledgerId)
            require(refundAllowed(original, input.amountCents))
        }
        val cats = categories.listByLedger(candidate.ledgerId)
        val category = original?.categoryId ?: MerchantRuleMatcher.firstHit(input.title, "", rules.listEnabledByPriority(candidate.ledgerId))?.categoryId
            ?: classifier.classify(input.title, "").ranked().firstOrNull()?.categoryId
        val now = System.currentTimeMillis()
        val txn = Txn(id = UUID.randomUUID().toString(), ledgerId = candidate.ledgerId, type = input.type,
            amount = Money.ofCents(input.amountCents), accountId = input.accountId, toAccountId = input.targetAccountId,
            categoryId = category?.takeIf { id -> cats.any { it.id == id && it.kind == TxnValidator.expectedKind(input.type) } },
            occurredAt = candidate.occurredAt, title = input.title, source = TxnSource.AUTO,
            tagIds = original?.tagIds.orEmpty(), excludeFromStats = original?.excludeFromStats ?: false,
            refundOfTxnId = original?.id, createdAt = now, updatedAt = now)
        TxnValidator.validate(txn, usable, cats)?.let { throw Rejected(it) }
        when (val result = txns.insert(txn)) {
            is AppResult.Failure -> throw Rejected(result.error)
            is AppResult.Success -> Unit
        }
        balances.syncBalances(listOfNotNull(txn.accountId, txn.toAccountId), now)
        dao.saveCandidate(candidate.copy(status = CandidateStatus.POSTED, txnId = txn.id,
            reason = ReviewReason.NONE, accountId = input.accountId, targetAccountId = input.targetAccountId,
            refundOfTxnId = original?.id).entity())
        return txn
    }

    private suspend fun refundAllowed(original: Txn, cents: Long): Boolean {
        if (original.type != TransactionType.EXPENSE || original.isDeleted || original.isDraft || cents <= 0) return false
        val refunded = txns.listByLedger(original.ledgerId).filter {
            it.type == TransactionType.REFUND && it.refundOfTxnId == original.id && it.draftStatus == DraftStatus.CONFIRMED
        }.fold(Money.ZERO) { total, txn -> total + txn.amount }
        return Money.ofCents(cents) <= original.amount - refunded
    }

    override suspend fun ignore(id: String) = guarded {
        database.withTransaction {
            val value = requireNotNull(dao.candidate(id)?.domain())
            require(value.status == CandidateStatus.PENDING)
            dao.saveCandidate(value.copy(status = CandidateStatus.IGNORED).entity())
        }
    }

    override suspend fun reopen(id: String) = guarded {
        database.withTransaction {
            val value = requireNotNull(dao.candidate(id)?.domain())
            require(value.status == CandidateStatus.IGNORED)
            // 凭据（auto_evidence）保持不变：同一笔支付重放仍不会二次入账。
            dao.saveCandidate(value.copy(status = CandidateStatus.PENDING).entity())
        }
    }

    override suspend fun hasSimilarCandidate(
        ledgerId: String,
        occurredAt: Long,
        amountCents: Long,
        type: TransactionType,
    ): Boolean = database.withTransaction {
        // [occurredAt] 是 AI 草稿的日界零点；同日候选落在 [occurredAt, occurredAt + 24h)。
        dao.nearby(ledgerId, occurredAt, occurredAt + DAY_MILLIS)
            .map { it.domain() }
            .any {
                (it.status == CandidateStatus.PENDING || it.status == CandidateStatus.POSTED) &&
                    it.facts.amountCents == amountCents &&
                    (it.facts.type == null || it.facts.type == type)
            }
    }
    override suspend fun link(id: String, txnId: String) = guarded {
        database.withTransaction {
            val value = requireNotNull(dao.candidate(id)?.domain())
            val txn = requireNotNull(txns.getById(txnId))
            require(value.status == CandidateStatus.PENDING && txn.ledgerId == value.ledgerId && !txn.isDeleted)
            dao.saveCandidate(value.copy(status = CandidateStatus.LINKED, txnId = txnId).entity())
        }
    }
    private suspend fun remember(ledgerId: String, key: String, candidateId: String) {
        if (dao.evidence(ledgerId, key) == null) dao.addEvidence(AutoEvidenceEntity(ledgerId, key, candidateId))
    }
    private fun externalKey(app: PaymentApp, type: TransactionType?, id: String) = "external:$app:$type:$id"
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        /** AI 草稿 occurredAt 恒为日界零点；±1 天窗口覆盖跨零点截图与次日补拍。 */
        const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000L
    }
    private class Rejected(val error: AppError) : RuntimeException()
    private suspend fun <T> guarded(block: suspend () -> T): AppResult<T> = try {
        AppResult.success(block())
    } catch (e: CancellationException) { throw e
    } catch (e: Rejected) { AppResult.failure(e.error)
    } catch (e: IllegalArgumentException) { AppResult.failure(AppError.Validation("Invalid automatic entry"))
    } catch (e: Exception) { AppResult.failure(AppError.Storage("Automatic entry failed", e)) }
}
