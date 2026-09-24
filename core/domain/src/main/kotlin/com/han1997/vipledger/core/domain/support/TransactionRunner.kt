package com.han1997.vipledger.core.domain.support

/**
 * 事务边界抽象。
 *
 * 领域层规定「跨表写入必须在一个事务里」，但 `withTransaction` 属于 Room/Android，
 * 故在此开一个口子，由 `:core:data` 用 `RoomDatabase.withTransaction` 实现。
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}