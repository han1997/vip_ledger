package com.han1997.vipledger.core.domain.support

/** Stores the last selected account per ledger for faster consecutive entry creation. */
interface LastAccountStore {
    suspend fun getLastAccountId(ledgerId: String): String?

    suspend fun setLastAccountId(ledgerId: String, accountId: String)
}
