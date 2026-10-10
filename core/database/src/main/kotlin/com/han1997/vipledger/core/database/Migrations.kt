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
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE savings_goal (id TEXT NOT NULL PRIMARY KEY, ledger_id TEXT NOT NULL, account_id TEXT NOT NULL, name TEXT NOT NULL, target_cents INTEGER NOT NULL, deadline TEXT, archived INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(ledger_id) REFERENCES ledger(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_savings_goal_ledger_id ON savings_goal(ledger_id)")
        db.execSQL("CREATE TABLE savings_link (txn_id TEXT NOT NULL PRIMARY KEY, goal_id TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(goal_id) REFERENCES savings_goal(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(txn_id) REFERENCES txn(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX index_savings_link_goal_id ON savings_link(goal_id)")
        db.execSQL("CREATE TABLE home_card (ledger_id TEXT NOT NULL, card_key TEXT NOT NULL, position INTEGER NOT NULL, visible INTEGER NOT NULL, PRIMARY KEY(ledger_id,card_key), FOREIGN KEY(ledger_id) REFERENCES ledger(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE asset_baseline (account_id TEXT NOT NULL PRIMARY KEY, ledger_id TEXT NOT NULL, captured_at INTEGER NOT NULL, balance_cents INTEGER NOT NULL, included INTEGER NOT NULL, FOREIGN KEY(ledger_id) REFERENCES ledger(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_asset_baseline_ledger_id ON asset_baseline(ledger_id)")
        db.execSQL("INSERT INTO asset_baseline SELECT id,ledger_id,CAST(strftime('%s','now') AS INTEGER)*1000,current_balance_cents,CASE WHEN include_in_net_worth=1 AND is_archived=0 AND deleted_at IS NULL THEN 1 ELSE 0 END FROM account")
    }
}
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reimbursement (id TEXT NOT NULL PRIMARY KEY, ledger_id TEXT NOT NULL, title TEXT NOT NULL, counterparty TEXT NOT NULL, remark TEXT NOT NULL, closed INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(ledger_id) REFERENCES ledger(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reimbursement_ledger_id ON reimbursement(ledger_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reimbursement_item (txn_id TEXT NOT NULL PRIMARY KEY, reimbursement_id TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(reimbursement_id) REFERENCES reimbursement(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(txn_id) REFERENCES txn(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reimbursement_item_reimbursement_id ON reimbursement_item(reimbursement_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reimbursement_receipt (txn_id TEXT NOT NULL PRIMARY KEY, reimbursement_id TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(reimbursement_id) REFERENCES reimbursement(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(txn_id) REFERENCES txn(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reimbursement_receipt_reimbursement_id ON reimbursement_receipt(reimbursement_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS lending (id TEXT NOT NULL PRIMARY KEY, ledger_id TEXT NOT NULL, direction TEXT NOT NULL, counterparty TEXT NOT NULL, principal_cents INTEGER NOT NULL, account_id TEXT NOT NULL, due_date TEXT, remark TEXT NOT NULL, closed INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(ledger_id) REFERENCES ledger(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lending_ledger_id ON lending(ledger_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lending_account_id ON lending(account_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS lending_link (txn_id TEXT NOT NULL PRIMARY KEY, lending_id TEXT NOT NULL, kind TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(txn_id) REFERENCES txn(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(lending_id) REFERENCES lending(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lending_link_lending_id ON lending_link(lending_id)")
    }
}
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
