package com.han1997.vipledger.core.domain.support

import kotlinx.coroutines.flow.Flow

/**
 * 「当前账本」选择的持久化。多账本切换只影响界面上下文，
 * 不改动任何账目数据，所以是一个独立的小端口而不是仓库方法。
 */
interface CurrentLedgerStore {
    fun observeLedgerId(): Flow<String?>

    suspend fun setLedgerId(ledgerId: String)
}