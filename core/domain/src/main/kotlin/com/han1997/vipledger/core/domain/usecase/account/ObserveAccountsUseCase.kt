package com.han1997.vipledger.core.domain.usecase.account

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

/** 账户列表订阅（默认隐藏已归档账户）。 */
class ObserveAccountsUseCase(private val accountRepository: AccountRepository) {
    operator fun invoke(ledgerId: String, includeArchived: Boolean = false): Flow<List<Account>> =
        accountRepository.observeByLedger(ledgerId, includeArchived)
}