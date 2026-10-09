package com.han1997.vipledger.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE txn ADD COLUMN refund_of_txn_id TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS auto_config (id INTEGER NOT NULL PRIMARY KEY, ledgerId TEXT NOT NULL, enabled INTEGER NOT NULL, ocrEnabled INTEGER NOT NULL, enabledApps TEXT NOT NULL, verifiedApps TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS auto_binding (ledgerId TEXT NOT NULL, app TEXT NOT NULL, token TEXT NOT NULL, accountId TEXT NOT NULL, PRIMARY KEY(ledgerId, app, token))")
        db.execSQL("CREATE TABLE IF NOT EXISTS auto_candidate (id TEXT NOT NULL PRIMARY KEY, ledgerId TEXT NOT NULL, app TEXT NOT NULL, source TEXT NOT NULL, occurredAt INTEGER NOT NULL, facts TEXT NOT NULL, accountId TEXT, targetAccountId TEXT, status TEXT NOT NULL, reason TEXT NOT NULL, txnId TEXT, refundOfTxnId TEXT, duplicateTxnId TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_candidate_ledgerId_occurredAt ON auto_candidate(ledgerId, occurredAt)")
        db.execSQL("CREATE TABLE IF NOT EXISTS auto_evidence (ledgerId TEXT NOT NULL, `key` TEXT NOT NULL, candidateId TEXT NOT NULL, PRIMARY KEY(ledgerId, `key`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_evidence_candidateId ON auto_evidence(candidateId)")
    }
}
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS budget_limit (id TEXT NOT NULL PRIMARY KEY, ledger_id TEXT NOT NULL, month TEXT NOT NULL, scope_key TEXT NOT NULL, amount_cents INTEGER, updated_at INTEGER NOT NULL, FOREIGN KEY(ledger_id) REFERENCES ledger(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budget_limit_ledger_id_month_scope_key ON budget_limit(ledger_id, month, scope_key)")
        db.execSQL("CREATE TABLE IF NOT EXISTS budget_alert (ledger_id TEXT NOT NULL, month TEXT NOT NULL, scope_key TEXT NOT NULL, threshold INTEGER NOT NULL, notified_at INTEGER NOT NULL, PRIMARY KEY(ledger_id, month, scope_key, threshold), FOREIGN KEY(ledger_id) REFERENCES ledger(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
