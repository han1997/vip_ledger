package com.han1997.vipledger.core.domain.fake

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Attachment
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.AttachmentRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.repository.MerchantRuleRepository
import com.han1997.vipledger.core.domain.repository.MlSampleRepository
import com.han1997.vipledger.core.domain.repository.RecurringRuleRepository
import com.han1997.vipledger.core.domain.repository.TagRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.CurrentLedgerStore
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/** 直接执行块的事务实现；用事务次数断言「跨表写入在一个事务里」。 */
class FakeTransactionRunner : TransactionRunner {
    var transactionCount: Int = 0
        private set

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        transactionCount++
        return block()
    }
}

/** 「当前账本」的假实现：内存里保存一个 id，方便断言切换行为。 */
class FakeCurrentLedgerStore(initial: String? = null) : CurrentLedgerStore {
    private val state = MutableStateFlow(initial)

    val current: String? get() = state.value

    override fun observeLedgerId(): Flow<String?> = state

    override suspend fun setLedgerId(ledgerId: String) {
        state.value = ledgerId
    }
}

class FakeTimeProvider(
    var now: Long = 1_767_225_600_000L,
    override val zone: TimeZone = TimeZone.of("Asia/Shanghai"),
) : TimeProvider {
    override fun nowMillis(): Long = now
}

class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0
    override fun newId(): String = "$prefix-${++counter}"
}

class RecordingBalanceSyncer : AccountBalanceSyncer {
    val calls = mutableListOf<List<String>>()

    override suspend fun syncBalances(accountIds: Collection<String>, updatedAt: Long) {
        calls += accountIds.toList()
    }

    val syncedIds: List<String> get() = calls.flatten().distinct()
}

class FakeLedgerRepository(private val ledgers: MutableList<Ledger> = mutableListOf()) : LedgerRepository {
    override fun observeAll(includeArchived: Boolean): Flow<List<Ledger>> = MutableStateFlow(filtered(includeArchived))

    override suspend fun listAll(includeArchived: Boolean): List<Ledger> = filtered(includeArchived)

    private fun filtered(includeArchived: Boolean): List<Ledger> =
        ledgers.filter { includeArchived || it.archivedAt == null }

    override suspend fun getById(id: String): Ledger? = ledgers.firstOrNull { it.id == id }

    override suspend fun getDefault(): Ledger? = ledgers.firstOrNull { it.isDefault }

    override suspend fun count(): Int = ledgers.size

    override suspend fun upsert(ledger: Ledger): AppResult<Unit> {
        ledgers.removeAll { it.id == ledger.id }
        ledgers += ledger
        return AppResult.success(Unit)
    }

    override suspend fun setDefault(id: String, updatedAt: Long): AppResult<Unit> {
        val index = ledgers.indexOfFirst { it.id == id }
        if (index < 0) return AppResult.failure(AppError.NotFound("账本不存在"))
        ledgers.replaceAll { it.copy(isDefault = it.id == id, updatedAt = updatedAt) }
        return AppResult.success(Unit)
    }

    override suspend fun setArchived(id: String, archivedAt: Long?): AppResult<Unit> {
        val index = ledgers.indexOfFirst { it.id == id }
        if (index < 0) return AppResult.failure(AppError.NotFound("账本不存在"))
        ledgers[index] = ledgers[index].copy(archivedAt = archivedAt)
        return AppResult.success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        ledgers.removeAll { it.id == id }
        return AppResult.success(Unit)
    }
}

class FakeAccountRepository(private val accounts: MutableList<Account> = mutableListOf()) : AccountRepository {
    override fun observeByLedger(ledgerId: String, includeArchived: Boolean): Flow<List<Account>> =
        MutableStateFlow(filtered(ledgerId, includeArchived))

    override suspend fun listByLedger(ledgerId: String, includeArchived: Boolean): List<Account> =
        filtered(ledgerId, includeArchived)

    private fun filtered(ledgerId: String, includeArchived: Boolean): List<Account> =
        accounts.filter { it.ledgerId == ledgerId && it.deletedAt == null && (includeArchived || !it.isArchived) }

    override suspend fun getById(id: String): Account? = accounts.firstOrNull { it.id == id }

    override suspend fun upsert(account: Account): AppResult<Unit> {
        accounts.removeAll { it.id == account.id }
        accounts += account
        return AppResult.success(Unit)
    }

    override suspend fun updateBalance(accountId: String, balance: Money, updatedAt: Long): AppResult<Unit> {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0) return AppResult.failure(AppError.NotFound("账户不存在"))
        accounts[index] = accounts[index].copy(currentBalance = balance, updatedAt = updatedAt)
        return AppResult.success(Unit)
    }

    override suspend fun setArchived(accountId: String, archived: Boolean, updatedAt: Long): AppResult<Unit> {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0) return AppResult.failure(AppError.NotFound("账户不存在"))
        accounts[index] = accounts[index].copy(isArchived = archived, updatedAt = updatedAt)
        return AppResult.success(Unit)
    }

    override suspend fun softDelete(accountId: String, deletedAt: Long): AppResult<Unit> {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0) return AppResult.failure(AppError.NotFound("账户不存在"))
        accounts[index] = accounts[index].copy(deletedAt = deletedAt)
        return AppResult.success(Unit)
    }

    fun snapshot(): List<Account> = accounts.toList()
}

class FakeCategoryRepository(private val categories: MutableList<Category> = mutableListOf()) : CategoryRepository {
    override fun observeByLedger(ledgerId: String, includeArchived: Boolean): Flow<List<Category>> =
        MutableStateFlow(filtered(ledgerId, includeArchived))

    override suspend fun listByLedger(ledgerId: String, includeArchived: Boolean): List<Category> =
        filtered(ledgerId, includeArchived)

    private fun filtered(ledgerId: String, includeArchived: Boolean): List<Category> =
        categories.filter { it.ledgerId == ledgerId && it.deletedAt == null && (includeArchived || !it.isArchived) }

    override suspend fun getById(id: String): Category? = categories.firstOrNull { it.id == id }

    override suspend fun upsert(category: Category): AppResult<Unit> {
        categories.removeAll { it.id == category.id }
        categories += category
        return AppResult.success(Unit)
    }

    override suspend fun upsertAll(categories: List<Category>): AppResult<Unit> {
        this.categories.removeAll { existing -> categories.any { it.id == existing.id } }
        this.categories += categories
        return AppResult.success(Unit)
    }

    override suspend fun countChildren(parentId: String): Int = categories.count { it.parentId == parentId && it.deletedAt == null }

    override suspend fun softDelete(categoryId: String, deletedAt: Long): AppResult<Unit> {
        val index = categories.indexOfFirst { it.id == categoryId }
        if (index < 0) return AppResult.failure(AppError.NotFound("分类不存在"))
        categories[index] = categories[index].copy(deletedAt = deletedAt)
        return AppResult.success(Unit)
    }

    fun snapshot(): List<Category> = categories.toList()
}

class FakeTagRepository(private val tags: MutableList<Tag> = mutableListOf()) : TagRepository {
    override fun observeByLedger(ledgerId: String): Flow<List<Tag>> = MutableStateFlow(filtered(ledgerId))

    override suspend fun listByLedger(ledgerId: String): List<Tag> = filtered(ledgerId)

    private fun filtered(ledgerId: String): List<Tag> = tags.filter { it.ledgerId == ledgerId && it.deletedAt == null }

    override suspend fun listByIds(ids: Collection<String>): List<Tag> = tags.filter { it.id in ids }

    override suspend fun getById(id: String): Tag? = tags.firstOrNull { it.id == id }

    override suspend fun upsert(tag: Tag): AppResult<Unit> {
        tags.removeAll { it.id == tag.id }
        tags += tag
        return AppResult.success(Unit)
    }

    override suspend fun softDelete(tagId: String, deletedAt: Long): AppResult<Unit> {
        val index = tags.indexOfFirst { it.id == tagId }
        if (index < 0) return AppResult.failure(AppError.NotFound("标签不存在"))
        tags[index] = tags[index].copy(deletedAt = deletedAt)
        return AppResult.success(Unit)
    }

    fun snapshot(): List<Tag> = tags.toList()
}

class FakeTxnRepository(
    private val txns: MutableList<Txn> = mutableListOf(),
    private val zone: TimeZone = TimeZone.of("Asia/Shanghai"),
) : TxnRepository {
    /** 构造时就同步一次，让「先塞数据再订阅」的写法也能看到已存在的数据。 */
    private val state = MutableStateFlow(txns.toList())

    override fun observe(filter: TxnFilter): Flow<List<Txn>> = state.map { list -> select(list, filter) }

    override suspend fun list(filter: TxnFilter): List<Txn> = select(txns, filter)

    private fun select(source: List<Txn>, filter: TxnFilter): List<Txn> {
        val matched = source.filter { matches(it, filter) }
        return if (filter.limit != null) matched.take(filter.limit) else matched
    }

    override suspend fun getById(id: String): Txn? = txns.firstOrNull { it.id == id }

    override suspend fun listByAccount(accountId: String, includeDeleted: Boolean): List<Txn> =
        txns.filter { (it.accountId == accountId || it.toAccountId == accountId) && (includeDeleted || it.deletedAt == null) }

    override suspend fun listByLedger(ledgerId: String, includeDeleted: Boolean): List<Txn> =
        txns.filter { it.ledgerId == ledgerId && (includeDeleted || it.deletedAt == null) }

    override suspend fun insert(txn: Txn): AppResult<Unit> {
        txns += txn
        publish()
        return AppResult.success(Unit)
    }

    override suspend fun update(txn: Txn): AppResult<Unit> {
        val index = txns.indexOfFirst { it.id == txn.id }
        if (index < 0) return AppResult.failure(AppError.NotFound("账目不存在"))
        txns[index] = txn
        publish()
        return AppResult.success(Unit)
    }

    override suspend fun softDelete(id: String, deletedAt: Long): AppResult<Unit> {
        val index = txns.indexOfFirst { it.id == id }
        if (index < 0) return AppResult.failure(AppError.NotFound("账目不存在"))
        txns[index] = txns[index].copy(deletedAt = deletedAt, updatedAt = deletedAt)
        publish()
        return AppResult.success(Unit)
    }

    override suspend fun restore(id: String, updatedAt: Long): AppResult<Unit> {
        val index = txns.indexOfFirst { it.id == id }
        if (index < 0) return AppResult.failure(AppError.NotFound("账目不存在"))
        txns[index] = txns[index].copy(deletedAt = null, updatedAt = updatedAt)
        publish()
        return AppResult.success(Unit)
    }

    override suspend fun existsImportHash(hash: String): Boolean = txns.any { it.importHash == hash }

    override suspend fun countByLedger(ledgerId: String): Int = txns.count { it.ledgerId == ledgerId && it.deletedAt == null }

    override suspend fun countByCategory(categoryId: String): Int =
        txns.count { it.categoryId == categoryId && it.deletedAt == null }

    fun snapshot(): List<Txn> = txns.toList()

    private fun publish() {
        state.value = txns.toList()
    }

    private fun matches(txn: Txn, filter: TxnFilter): Boolean {
        if (txn.ledgerId != filter.ledgerId) return false
        if (!filter.includeDeleted && txn.deletedAt != null) return false
        if (txn.type !in filter.types) return false
        if (filter.draftStatus != null && txn.draftStatus != filter.draftStatus) return false
        if (filter.accountIds.isNotEmpty() && txn.accountId !in filter.accountIds && txn.toAccountId !in filter.accountIds) return false
        if (filter.categoryIds.isNotEmpty() && txn.categoryId !in filter.categoryIds) return false
        if (filter.tagIds.isNotEmpty() && txn.tagIds.none { it in filter.tagIds }) return false
        if (filter.minAmount != null && txn.amount.abs() < filter.minAmount) return false
        if (filter.maxAmount != null && txn.amount.abs() > filter.maxAmount) return false
        if (filter.range != null && txn.occurredAt.toLocalDate(zone) !in filter.range) return false
        val keyword = filter.keyword
        if (!keyword.isNullOrBlank() && !txn.title.contains(keyword) && !txn.remark.contains(keyword)) return false
        return true
    }
}

class FakeInstallmentRepository(
    private val plans: MutableList<InstallmentPlan> = mutableListOf(),
    private val periods: MutableList<InstallmentPeriod> = mutableListOf(),
) : InstallmentRepository {
    override fun observePlans(ledgerId: String): Flow<List<InstallmentPlan>> = MutableStateFlow(filtered(ledgerId))

    override suspend fun listPlans(ledgerId: String): List<InstallmentPlan> = filtered(ledgerId)

    private fun filtered(ledgerId: String): List<InstallmentPlan> = plans.filter { it.ledgerId == ledgerId }

    override suspend fun getPlan(id: String): InstallmentPlan? = plans.firstOrNull { it.id == id }

    override suspend fun savePlanWithPeriods(plan: InstallmentPlan, periods: List<InstallmentPeriod>): AppResult<Unit> {
        plans.removeAll { it.id == plan.id }
        this.periods.removeAll { it.planId == plan.id }
        plans += plan
        this.periods += periods
        return AppResult.success(Unit)
    }

    override suspend fun updatePlanStatus(planId: String, status: InstallmentPlanStatus, updatedAt: Long): AppResult<Unit> {
        val index = plans.indexOfFirst { it.id == planId }
        if (index < 0) return AppResult.failure(AppError.NotFound("分期计划不存在"))
        plans[index] = plans[index].copy(status = status, updatedAt = updatedAt)
        return AppResult.success(Unit)
    }

    override suspend fun listPeriods(planId: String): List<InstallmentPeriod> =
        periods.filter { it.planId == planId }.sortedBy { it.periodNo }

    override suspend fun getPeriod(planId: String, periodNo: Int): InstallmentPeriod? =
        periods.firstOrNull { it.planId == planId && it.periodNo == periodNo }

    override suspend fun markPeriodPaid(planId: String, periodNo: Int, txnId: String, paidAt: Long): AppResult<Unit> {
        val index = periods.indexOfFirst { it.planId == planId && it.periodNo == periodNo }
        if (index < 0) return AppResult.failure(AppError.NotFound("分期不存在"))
        periods[index] = periods[index].copy(
            status = com.han1997.vipledger.core.model.InstallmentPeriodStatus.PAID,
            paidTxnId = txnId,
            paidAt = paidAt,
        )
        return AppResult.success(Unit)
    }

    override suspend fun softDeletePlan(planId: String, deletedAt: Long): AppResult<Unit> {
        val index = plans.indexOfFirst { it.id == planId }
        if (index < 0) return AppResult.failure(AppError.NotFound("分期计划不存在"))
        plans[index] = plans[index].copy(deletedAt = deletedAt)
        return AppResult.success(Unit)
    }

    fun planCount(): Int = plans.size

    fun periodSnapshot(): List<InstallmentPeriod> = periods.toList()
}

class FakeRecurringRuleRepository(private val rules: MutableList<RecurringRule> = mutableListOf()) : RecurringRuleRepository {
    override fun observeByLedger(ledgerId: String): Flow<List<RecurringRule>> = MutableStateFlow(filtered(ledgerId))

    override suspend fun listByLedger(ledgerId: String): List<RecurringRule> = filtered(ledgerId)

    private fun filtered(ledgerId: String): List<RecurringRule> = rules.filter { it.ledgerId == ledgerId && it.deletedAt == null }

    override suspend fun listEnabled(ledgerId: String): List<RecurringRule> = listByLedger(ledgerId).filter { it.enabled }

    override suspend fun getById(id: String): RecurringRule? = rules.firstOrNull { it.id == id }

    override suspend fun upsert(rule: RecurringRule): AppResult<Unit> {
        rules.removeAll { it.id == rule.id }
        rules += rule
        return AppResult.success(Unit)
    }

    override suspend fun markGenerated(
        ruleId: String,
        generatedCount: Int,
        nextOccurrenceAt: Long?,
        lastGeneratedAt: Long,
        updatedAt: Long,
    ): AppResult<Unit> {
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index < 0) return AppResult.failure(AppError.NotFound("周期规则不存在"))
        rules[index] = rules[index].copy(
            generatedCount = generatedCount,
            nextOccurrenceAt = nextOccurrenceAt,
            lastGeneratedAt = lastGeneratedAt,
            updatedAt = updatedAt,
        )
        return AppResult.success(Unit)
    }

    override suspend fun setEnabled(ruleId: String, enabled: Boolean, updatedAt: Long): AppResult<Unit> {
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index < 0) return AppResult.failure(AppError.NotFound("周期规则不存在"))
        rules[index] = rules[index].copy(enabled = enabled, updatedAt = updatedAt)
        return AppResult.success(Unit)
    }

    override suspend fun softDelete(ruleId: String, deletedAt: Long): AppResult<Unit> {
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index < 0) return AppResult.failure(AppError.NotFound("周期规则不存在"))
        rules[index] = rules[index].copy(deletedAt = deletedAt)
        return AppResult.success(Unit)
    }
}

class FakeMerchantRuleRepository(private val rules: MutableList<MerchantRule> = mutableListOf()) : MerchantRuleRepository {
    override fun observeByLedger(ledgerId: String): Flow<List<MerchantRule>> = MutableStateFlow(filtered(ledgerId))

    override suspend fun listByLedger(ledgerId: String): List<MerchantRule> = filtered(ledgerId)

    private fun filtered(ledgerId: String): List<MerchantRule> = rules.filter { it.ledgerId == ledgerId }

    override suspend fun listEnabledByPriority(ledgerId: String): List<MerchantRule> =
        listByLedger(ledgerId).filter { it.enabled }.sortedBy { it.priority }

    override suspend fun upsert(rule: MerchantRule): AppResult<Unit> {
        rules.removeAll { it.id == rule.id }
        rules += rule
        return AppResult.success(Unit)
    }

    override suspend fun incrementHitCount(ruleId: String, updatedAt: Long): AppResult<Unit> {
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index < 0) return AppResult.failure(AppError.NotFound("规则不存在"))
        rules[index] = rules[index].copy(hitCount = rules[index].hitCount + 1, updatedAt = updatedAt)
        return AppResult.success(Unit)
    }

    override suspend fun delete(ruleId: String): AppResult<Unit> {
        rules.removeAll { it.id == ruleId }
        return AppResult.success(Unit)
    }
}

class FakeAttachmentRepository(private val attachments: MutableList<Attachment> = mutableListOf()) : AttachmentRepository {
    override fun observeByTxn(txnId: String): Flow<List<Attachment>> = MutableStateFlow(filtered(txnId))

    override suspend fun listByTxn(txnId: String): List<Attachment> = filtered(txnId)

    private fun filtered(txnId: String): List<Attachment> = attachments.filter { it.txnId == txnId && it.deletedAt == null }

    override suspend fun insertAll(attachments: List<Attachment>): AppResult<Unit> {
        this.attachments += attachments
        return AppResult.success(Unit)
    }

    override suspend fun softDelete(attachmentId: String, deletedAt: Long): AppResult<Unit> {
        val index = attachments.indexOfFirst { it.id == attachmentId }
        if (index < 0) return AppResult.failure(AppError.NotFound("附件不存在"))
        attachments[index] = attachments[index].copy(deletedAt = deletedAt)
        return AppResult.success(Unit)
    }
}

class FakeMlSampleRepository(private val samples: MutableList<MlTrainingSample> = mutableListOf()) : MlSampleRepository {
    override fun observeByLedger(ledgerId: String): Flow<List<MlTrainingSample>> = MutableStateFlow(filtered(ledgerId))

    override suspend fun listByLedger(ledgerId: String): List<MlTrainingSample> = filtered(ledgerId)

    private fun filtered(ledgerId: String): List<MlTrainingSample> = samples.filter { it.ledgerId == ledgerId }

    override suspend fun listUntrained(ledgerId: String): List<MlTrainingSample> =
        listByLedger(ledgerId).filter { it.trainedVersion == null }

    override suspend fun insertAll(samples: List<MlTrainingSample>): AppResult<Unit> {
        this.samples += samples
        return AppResult.success(Unit)
    }

    override suspend fun markTrained(ids: Collection<String>, version: String): AppResult<Unit> {
        samples.replaceAll { if (it.id in ids) it.copy(trainedVersion = version) else it }
        return AppResult.success(Unit)
    }

    override suspend fun countByLedger(ledgerId: String): Int = listByLedger(ledgerId).size
}