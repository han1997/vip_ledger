package com.han1997.vipledger.core.data.support

import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.database.dao.AccountDao
import com.han1997.vipledger.core.database.dao.TxnDao
import com.han1997.vipledger.core.domain.calculator.BalanceCalculator
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import javax.inject.Inject

/**
 * 按流水全量重算账户余额缓存（docs/DATA-MODEL.md 第 6 节）。
 * 走 account_id 索引只扫本账户的流水，改流水/软删/换账户都不会算错。
 */
class AccountBalanceSyncerImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val txnDao: TxnDao,
) : AccountBalanceSyncer {

    override suspend fun syncBalances(accountIds: Collection<String>, updatedAt: Long) {
        accountIds.distinct().forEach { accountId ->
            val account = accountDao.getById(accountId) ?: return@forEach
            val txns = txnDao.listByAccount(accountId, includeDeleted = false).map { it.toDomain() }
            val balance = BalanceCalculator.balanceOf(account.toDomain(), txns)
            accountDao.updateBalance(accountId, balance.cents, updatedAt)
        }
    }
}