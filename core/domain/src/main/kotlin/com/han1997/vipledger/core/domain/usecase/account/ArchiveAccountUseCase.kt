package com.han1997.vipledger.core.domain.usecase.account

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.repository.AccountRepository

/**
 * 归档 / 解除归档账户。归档只是从记账选择器里隐藏、不参与净资产，
 * 流水原样保留；因此必须至少留一个可用账户，否则没法继续记账。
 */
class ArchiveAccountUseCase(
    private val accountRepository: AccountRepository,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(accountId: String, archived: Boolean): AppResult<Unit> {
        val account = accountRepository.getById(accountId)
            ?: return AppResult.failure(AppError.NotFound("账户不存在"))
        if (account.deletedAt != null) {
            return AppResult.failure(AppError.Validation("账户已删除"))
        }
        if (account.isArchived == archived) return AppResult.success(Unit)

        if (archived) {
            val usable = accountRepository.listByLedger(account.ledgerId).filter { it.id != accountId }
            if (usable.isEmpty()) {
                return AppResult.failure(AppError.Validation("至少要保留一个可用账户"))
            }
        }
        return accountRepository.setArchived(accountId, archived, timeProvider.nowMillis())
    }
}