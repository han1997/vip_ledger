package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.CategoryRepository
import com.han1997.vipledger.core.domain.repository.LedgerRepository
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner

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
            accountRepository.upsert(BuiltinDataFactory.cashAccount(ledger.id, idGenerator, now))
            categoryRepository.upsertAll(BuiltinDataFactory.categories(builtinCategorySource, ledger.id, idGenerator, now))
            AppResult.success(ledger)
        }
    }

    companion object {
        const val DEFAULT_LEDGER_NAME: String = "默认账本"
        const val DEFAULT_ACCOUNT_NAME: String = BuiltinDataFactory.DEFAULT_CASH_ACCOUNT_NAME
    }
}