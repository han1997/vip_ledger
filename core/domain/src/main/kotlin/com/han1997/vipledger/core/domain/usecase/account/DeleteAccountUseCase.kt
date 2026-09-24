package com.han1997.vipledger.core.domain.usecase.account

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.TxnRepository

/**
 * 删除账户（软删）。账户已被流水引用时拒绝删除——软删账户会让历史账目失去归属，
 * 正确做法是先归档；确实要删就得先处理流水。
 */
class DeleteAccountUseCase(
    private val accountRepository: AccountRepository,
    private val txnRepository: TxnRepository,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(accountId: String): AppResult<Unit> {
        val account = accountRepository.getById(accountId)
            ?: return AppResult.failure(AppError.NotFound("账户不存在"))
        if (txnRepository.listByAccount(accountId, includeDeleted = true).isNotEmpty()) {
            return AppResult.failure(AppError.Validation("账户下还有流水，请改为归档"))
        }
        val remaining = accountRepository.listByLedger(account.ledgerId).filter { it.id != accountId }
        if (remaining.isEmpty()) {
            return AppResult.failure(AppError.Validation("至少要保留一个可用账户"))
        }
        return accountRepository.softDelete(accountId, timeProvider.nowMillis())
    }
}