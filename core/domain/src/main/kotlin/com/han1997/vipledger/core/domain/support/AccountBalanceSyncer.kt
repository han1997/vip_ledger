package com.han1997.vipledger.core.domain.support

/**
 * 账户余额缓存同步（docs/DATA-MODEL.md 第 6 节）。
 *
 * 实现必须按流水全量重算，而不是做增量加减：改流水、软删、换账户都不会算错。
 * 必须在调用方的事务内执行。
 */
interface AccountBalanceSyncer {
    suspend fun syncBalances(accountIds: Collection<String>, updatedAt: Long)
}