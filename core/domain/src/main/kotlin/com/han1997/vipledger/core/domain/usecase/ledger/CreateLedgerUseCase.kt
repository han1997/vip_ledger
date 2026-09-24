package com.han1997.vipledger.core.domain.usecase.ledger

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner
import com.han1997.vipledger.core.domain.usecase.BuiltinDataFactory

/**
 * 新建账本。新账本自带默认现金账户与内置两级分类，写入在同一事务内完成，
 * 避免出现「有账本但没法记账」的中间态。
 */
class CreateLedgerUseCase(
    private val ledgerRepository: LedgerRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val builtinCategorySource: BuiltinCategorySource,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(name: String, iconKey: String? = null): AppResult<Ledger> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AppResult.failure(AppError.Validation("账本名不能为空"))
        }
        val existing = ledgerRepository.listAll(includeArchived = true)
        if (existing.any { it.name == trimmed }) {
            return AppResult.failure(AppError.Validation("已存在同名账本：" + trimmed))
        }
        if (existing.size >= MAX_LEDGERS) {
            return AppResult.failure(AppError.Validation("账本数量已达上限（" + MAX_LEDGERS + " 个）"))
        }

        val now = timeProvider.nowMillis()
        val ledger = Ledger(
            id = idGenerator.newId(),
            name = trimmed,
            iconKey = iconKey,
            sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            isDefault = existing.isEmpty(),
            createdAt = now,
            updatedAt = now,
        )
        return transactionRunner.inTransaction {
            ledgerRepository.upsert(ledger)
            accountRepository.upsert(BuiltinDataFactory.cashAccount(ledger.id, idGenerator, now))
            categoryRepository.upsertAll(
                BuiltinDataFactory.categories(builtinCategorySource, ledger.id, idGenerator, now),
            )
            AppResult.success(ledger)
        }
    }

    companion object {
        const val MAX_LEDGERS: Int = 20
    }
}