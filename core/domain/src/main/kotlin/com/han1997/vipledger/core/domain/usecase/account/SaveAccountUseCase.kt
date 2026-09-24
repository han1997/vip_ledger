package com.han1997.vipledger.core.domain.usecase.account

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.result.map
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.AccountDraft
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.TransactionRunner

/**
 * 新建（[accountId] 为 null）或编辑资金账户。
 *
 * 期初余额变化时必须重算余额缓存，否则「期初 + 流水 = 余额」这条不变式会被破坏；
 * 重算与写入放在同一个事务里。
 */
class SaveAccountUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val balanceSyncer: AccountBalanceSyncer,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(accountId: String?, draft: AccountDraft): AppResult<Account> {
        val name = draft.name.trim()
        if (name.isEmpty()) {
            return AppResult.failure(AppError.Validation("账户名不能为空"))
        }
        draft.creditLimit?.let { limit ->
            if (limit.isNegative) return AppResult.failure(AppError.Validation("额度不能为负数"))
        }
        draft.billingDay?.let { day ->
            if (day !in 1..31) return AppResult.failure(AppError.Validation("账单日必须是 1~31 号"))
        }
        draft.dueDay?.let { day ->
            if (day !in 1..31) return AppResult.failure(AppError.Validation("还款日必须是 1~31 号"))
        }

        val siblings = accountRepository.listByLedger(draft.ledgerId, includeArchived = true)
        if (siblings.any { it.id != accountId && it.name == name }) {
            return AppResult.failure(AppError.Validation("已存在同名账户：" + name))
        }

        val existing = accountId?.let { accountRepository.getById(it) }
        if (accountId != null && existing == null) {
            return AppResult.failure(AppError.NotFound("账户不存在"))
        }

        val now = timeProvider.nowMillis()
        val account = if (existing == null) {
            Account(
                id = idGenerator.newId(),
                ledgerId = draft.ledgerId,
                name = name,
                type = draft.type,
                iconKey = draft.iconKey,
                initialBalance = draft.initialBalance,
                currentBalance = draft.initialBalance,
                creditLimit = draft.creditLimit,
                billingDay = draft.billingDay,
                dueDay = draft.dueDay,
                includeInNetWorth = draft.includeInNetWorth,
                sortOrder = siblings.size,
                remark = draft.remark,
                createdAt = now,
                updatedAt = now,
            )
        } else {
            existing.copy(
                name = name,
                type = draft.type,
                iconKey = draft.iconKey ?: existing.iconKey,
                initialBalance = draft.initialBalance,
                creditLimit = draft.creditLimit,
                billingDay = draft.billingDay,
                dueDay = draft.dueDay,
                includeInNetWorth = draft.includeInNetWorth,
                remark = draft.remark,
                updatedAt = now,
            )
        }

        val balanceChanged = existing != null && existing.initialBalance != account.initialBalance
        return transactionRunner.inTransaction {
            val result = accountRepository.upsert(account)
            if (result.isSuccess && (balanceChanged || existing == null)) {
                balanceSyncer.syncBalances(listOf(account.id), now)
            }
            result.map { account }
        }
    }
}