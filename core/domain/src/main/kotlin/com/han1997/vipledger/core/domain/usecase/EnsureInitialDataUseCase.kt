package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.support.BuiltinCategory
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.money.Money

/**
 * 首次启动的初始化：保证「有一个默认账本」这条不变式。
 *
 * 空库时建默认账本 + 默认现金账户 + 内置两级分类；已有账本但没默认时把第一个设为默认。
 * 幂等：重复调用不会重复建数据。
 */
class EnsureInitialDataUseCase(
    private val ledgerRepository: LedgerRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val builtinCategorySource: BuiltinCategorySource,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(): AppResult<Ledger> {
        ledgerRepository.getDefault()?.let { return AppResult.success(it) }

        val existing = ledgerRepository.listAll(includeArchived = true)
        if (existing.isNotEmpty()) {
            val first = existing.first()
            val result = ledgerRepository.setDefault(first.id, timeProvider.nowMillis())
            return when (result) {
                is AppResult.Success -> AppResult.success(first.copy(isDefault = true))
                is AppResult.Failure -> result
            }
        }

        val now = timeProvider.nowMillis()
        val ledger = Ledger(
            id = idGenerator.newId(),
            name = DEFAULT_LEDGER_NAME,
            isDefault = true,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
        )
        return transactionRunner.inTransaction {
            ledgerRepository.upsert(ledger)
            accountRepository.upsert(defaultCashAccount(ledger.id, now))
            categoryRepository.upsertAll(builtinCategories(ledger.id, now))
            AppResult.success(ledger)
        }
    }

    private fun defaultCashAccount(ledgerId: String, now: Long) = Account(
        id = idGenerator.newId(),
        ledgerId = ledgerId,
        name = DEFAULT_ACCOUNT_NAME,
        type = AccountType.CASH,
        initialBalance = Money.ZERO,
        currentBalance = Money.ZERO,
        sortOrder = 0,
        createdAt = now,
        updatedAt = now,
    )

    private fun builtinCategories(ledgerId: String, now: Long): List<Category> {
        val result = mutableListOf<Category>()
        var topSort = 0
        builtinCategorySource.categories().forEach { builtin ->
            val parentId = idGenerator.newId()
            result += builtin.toCategory(ledgerId, parentId, null, topSort++, now)
            builtin.children.forEachIndexed { index, child ->
                result += child.toCategory(ledgerId, idGenerator.newId(), parentId, index, now)
            }
        }
        return result
    }

    private fun BuiltinCategory.toCategory(
        ledgerId: String,
        id: String,
        parentId: String?,
        sortOrder: Int,
        now: Long,
    ) = Category(
        id = id,
        ledgerId = ledgerId,
        parentId = parentId,
        name = name,
        kind = kind,
        iconKey = iconKey,
        sortOrder = sortOrder,
        isSystem = true,
        createdAt = now,
        updatedAt = now,
    )

    companion object {
        const val DEFAULT_LEDGER_NAME: String = "默认账本"
        const val DEFAULT_ACCOUNT_NAME: String = "现金"
    }
}