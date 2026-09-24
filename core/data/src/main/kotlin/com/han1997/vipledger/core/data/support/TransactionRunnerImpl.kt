package com.han1997.vipledger.core.data.support

import androidx.room.withTransaction
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.domain.support.TransactionRunner
import javax.inject.Inject

/** 用 Room 的事务实现领域层的 [TransactionRunner]（docs/ARCHITECTURE.md 的硬规则）。 */
class TransactionRunnerImpl @Inject constructor(
    private val database: LedgerDatabase,
) : TransactionRunner {

    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}