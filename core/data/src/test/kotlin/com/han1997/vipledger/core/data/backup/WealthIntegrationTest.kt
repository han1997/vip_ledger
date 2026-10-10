package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.common.result.*
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.repository.*
import com.han1997.vipledger.core.data.support.AccountBalanceSyncerImpl
import com.han1997.vipledger.core.data.support.TransactionRunnerImpl
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.usecase.txn.*
import com.han1997.vipledger.core.domain.usecase.wealth.*
import com.han1997.vipledger.core.model.*
import com.han1997.vipledger.core.model.money.Money
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class WealthIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: LedgerDatabase
    private lateinit var wealth: WealthRepositoryImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var txns: TxnRepositoryImpl
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var runner: TransactionRunnerImpl
    private lateinit var syncer: AccountBalanceSyncerImpl
    private lateinit var save: SaveSavingsGoalUseCase
    private lateinit var move: MoveSavingsUseCase
    private lateinit var create: CreateTxnUseCase
    private lateinit var guard: SavingsTxnGuard
    private val time = object : TimeProvider {
        override fun nowMillis() = 1_791_584_000_000L
        override val zone = TimeZone.of("Asia/Shanghai")
    }
    private val ids = object : IdGenerator { override fun newId() = UUID.randomUUID().toString() }
    @BeforeTest fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).allowMainThreadQueries().build()
        wealth = WealthRepositoryImpl(db); accounts = AccountRepositoryImpl(db.accountDao())
        txns = TxnRepositoryImpl(db.txnDao(), time); categories = CategoryRepositoryImpl(db.categoryDao())
        runner = TransactionRunnerImpl(db); syncer = AccountBalanceSyncerImpl(db.accountDao(), db.txnDao())
        create = CreateTxnUseCase(txns, accounts, categories, runner, syncer, time, ids)
        save = SaveSavingsGoalUseCase(wealth, accounts, runner, time, ids)
        move = MoveSavingsUseCase(wealth, txns, accounts, create, runner, time)
        guard = SavingsTxnGuard(wealth, txns)
    }
    @AfterTest fun close() { db.close() }
    private suspend fun seed(): String {
        db.ledgerDao().upsert(LedgerEntity("l1", "One", createdAt = 1, updatedAt = 1))
        db.ledgerDao().upsert(LedgerEntity("l2", "Two", createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(AccountEntity("cash", "l1", "Cash", AccountType.CASH, initialBalanceCents = 100000, currentBalanceCents = 100000, createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(AccountEntity("stash", "l1", "Savings", AccountType.BANK_DEBIT, createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(AccountEntity("foreign", "l2", "Foreign", AccountType.CASH, createdAt = 1, updatedAt = 1))
        save(null, "l1", "stash", "Trip", Money.ofCents(20000), null).getOrThrow()
        return wealth.observeGoals("l1").first().single().id
    }
    private suspend fun progress() = ObserveSavingsUseCase(wealth, txns)("l1").first().single()

    @Test fun ledgerRenamePreservesAllAccountsEntriesAndSavingsLinks() = runTest {
        val goal = seed()
        move(goal, "cash", Money.ofCents(10000), true, "Deposit").getOrThrow()
        val original = db.ledgerDao().getById("l1")!!
        db.ledgerDao().upsert(original.copy(name = "Renamed"))
        assertEquals(1, db.txnDao().countByLedger("l1"))
        assertEquals(2, accounts.listByLedger("l1").size)
        assertEquals(10000L, progress().saved.cents)
        assertEquals(1, wealth.links(goal).size)
        assertFails { db.ledgerDao().upsert(original.copy(name = "Two")) }
        assertEquals("Renamed", db.ledgerDao().getById("l1")!!.name)
        assertEquals(2, db.ledgerDao().count())
    }

    @Test fun invalidCrossLedgerGoalInSnapshotDoesNotReplaceLiveData() = runTest {
        val id = seed()
        val original = wealth.goal(id)!!
        val file = File(context.cacheDir, "wealth-invalid-${System.nanoTime()}.zip")
        try {
            wealth.saveGoal(original.copy(accountId = "foreign")).getOrThrow()
            val archive = SnapshotArchive(context, db)
            archive.create(file)
            wealth.saveGoal(original).getOrThrow()
            assertFails { archive.restore(file) }
            assertEquals("stash", wealth.goal(id)!!.accountId)
            assertEquals(100000L, accounts.getById("cash")!!.currentBalance.cents)
        } finally { file.delete() }
    }

    @Test fun depositsWithdrawalsRenamingAndArchivePreserveLinksAndNetWorth() = runTest {
        val goal = seed()
        move(goal, "cash", Money.ofCents(20000), true, "Deposit").getOrThrow()
        assertTrue(progress().achieved)
        move(goal, "cash", Money.ofCents(5000), false, "Withdraw").getOrThrow()
        assertEquals(15000L, progress().saved.cents); assertFalse(progress().achieved)
        assertEquals(85000L, accounts.getById("cash")!!.currentBalance.cents)
        assertEquals(15000L, accounts.getById("stash")!!.currentBalance.cents)
        val assets = ObserveAssetsUseCase(accounts, wealth, time)("l1").first { it.baselines.size == 2 }
        assertEquals(100000L, assets.netWorth.cents)
        save(goal, "l1", "stash", "New name", Money.ofCents(15000), null).getOrThrow()
        save.archive(goal, true).getOrThrow()
        assertEquals(2, wealth.links(goal).size); assertTrue(progress().goal.archived)
        save.archive(goal, false).getOrThrow()
        assertTrue(progress().achieved)
        assertEquals("New name", progress().goal.name)
        assertTrue(wealth.observeGoals("l2").first().isEmpty())
    }

    @Test fun editsDeletesAndRestoresCannotDetachSavingsOrCreateNegativeProgress() = runTest {
        val goal = seed()
        val deposit = move(goal, "cash", Money.ofCents(20000), true, "Deposit").getOrThrow()
        val withdrawal = move(goal, "cash", Money.ofCents(5000), false, "Withdraw").getOrThrow()
        val update = UpdateTxnUseCase(txns, accounts, categories, runner, syncer, time, guard)
        val delete = DeleteTxnUseCase(txns, runner, syncer, time, guard)
        assertFalse(update(deposit.copy(amount = Money.ofCents(4000))).isSuccess)
        assertFalse(update(deposit.copy(accountId = "stash", toAccountId = "cash")).isSuccess)
        assertFalse(delete(deposit.id).isSuccess)
        update(deposit.copy(amount = Money.ofCents(10000))).getOrThrow()
        assertEquals(5000L, progress().saved.cents)
        assertEquals(2, wealth.links(goal).size, "UPDATE must retain links")
        delete(withdrawal.id).getOrThrow()
        delete(deposit.id).getOrThrow()
        assertEquals(0L, progress().saved.cents)
        assertFalse(delete.restore(withdrawal.id).isSuccess)
        delete.restore(deposit.id).getOrThrow()
        delete.restore(withdrawal.id).getOrThrow()
        assertEquals(5000L, progress().saved.cents)
        assertEquals(95000L, accounts.getById("cash")!!.currentBalance.cents)
    }

    @Test fun foreignAccountsAndChangedGoalAccountsAreRejectedWithoutPosting() = runTest {
        val goal = seed()
        assertFalse(move(goal, "foreign", Money.ofCents(100), true, "bad").isSuccess)
        assertFalse(save(null, "l1", "foreign", "Wrong", Money.ofCents(100), null).isSuccess)
        assertEquals(0, db.txnDao().countByLedger("l1"))
        move(goal, "cash", Money.ofCents(10000), true, "Deposit").getOrThrow()
        assertFalse(save(goal, "l1", "cash", "Wrong", Money.ofCents(100), null).isSuccess)
        assertEquals("stash", wealth.goal(goal)!!.accountId)
    }

    @Test fun associationFailureRollsBackTransferAndBothAccountBalances() = runTest {
        val goal = seed()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_link BEFORE INSERT ON savings_link BEGIN SELECT RAISE(ABORT,'fixture'); END")
        assertFalse(move(goal, "cash", Money.ofCents(10000), true, "Deposit").isSuccess)
        assertEquals(0, db.txnDao().countByLedger("l1"))
        assertEquals(100000L, accounts.getById("cash")!!.currentBalance.cents)
        assertEquals(0L, accounts.getById("stash")!!.currentBalance.cents)
        assertTrue(wealth.links(goal).isEmpty())
    }

    @Test fun tagWriteFailureRollsBackEditedSavingsEntryBeforeReturningFailure() = runTest {
        val goal = seed()
        val deposit = move(goal, "cash", Money.ofCents(20000), true, "Deposit").getOrThrow()
        db.tagDao().upsert(com.han1997.vipledger.core.database.entity.TagEntity("review", "l1", "Review", createdAt = 1, updatedAt = 1))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_tag BEFORE INSERT ON txn_tag BEGIN SELECT RAISE(ABORT,'fixture'); END")
        val update = UpdateTxnUseCase(txns, accounts, categories, runner, syncer, time, guard)
        assertFalse(update(deposit.copy(amount = Money.ofCents(30000), tagIds = listOf("review"))).isSuccess)
        assertEquals(20000L, txns.getById(deposit.id)!!.amount.cents)
        assertEquals(20000L, progress().saved.cents)
        assertEquals(80000L, accounts.getById("cash")!!.currentBalance.cents)
        assertEquals(20000L, accounts.getById("stash")!!.currentBalance.cents)
        assertTrue(txns.getById(deposit.id)!!.tagIds.isEmpty())
        assertEquals(1, wealth.links(goal).size)
    }

    @Test fun concurrentWithdrawalsSerializeTheirBalanceCheck() = runTest {
        val goal = seed()
        move(goal, "cash", Money.ofCents(10000), true, "Deposit").getOrThrow()
        val results = List(2) { async { move(goal, "cash", Money.ofCents(8000), false, "Withdraw") } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(2000L, progress().saved.cents)
        assertEquals(98000L, accounts.getById("cash")!!.currentBalance.cents)
    }

    @Test fun calibrationPostsOnlyTheDifferenceAndDoesNotAlterSavings() = runTest {
        val goal = seed()
        move(goal, "cash", Money.ofCents(10000), true, "Deposit").getOrThrow()
        val adjust = AdjustAccountBalanceUseCase(accounts, create, runner)
        adjust("cash", Money.ofCents(80000), "Adjustment").getOrThrow()
        assertEquals(80000L, accounts.getById("cash")!!.currentBalance.cents)
        assertEquals(10000L, progress().saved.cents)
        val count = db.txnDao().countByLedger("l1")
        adjust("cash", Money.ofCents(80000), "No change").getOrThrow()
        assertEquals(count, db.txnDao().countByLedger("l1"))
    }

    @Test fun fullBackupRestoresGoalsLayoutsLinksAndImmutableBaseline() = runTest {
        val goal = seed()
        move(goal, "cash", Money.ofCents(20000), true, "Deposit").getOrThrow()
        wealth.ensureBaselines(accounts.listByLedger("l1"), 100).getOrThrow()
        val layout = HomeCardLayout.move(HomeCardLayout.defaults(), HomeCard.SAVINGS, -2).map { if (it.card == HomeCard.BUDGET) it.copy(visible = false) else it }
        wealth.saveCards("l1", layout).getOrThrow()
        wealth.ensureBaselines(accounts.listByLedger("l1"), 999).getOrThrow()
        assertTrue(wealth.observeBaselines("l1").first().all { it.capturedAt == 100L })
        val file = File(context.cacheDir, "wealth-${System.nanoTime()}.zip")
        try {
            val archive = SnapshotArchive(context, db)
            archive.create(file)
            db.openHelper.writableDatabase.execSQL("DELETE FROM savings_link")
            db.openHelper.writableDatabase.execSQL("DELETE FROM savings_goal")
            db.openHelper.writableDatabase.execSQL("DELETE FROM home_card")
            db.openHelper.writableDatabase.execSQL("DELETE FROM asset_baseline")
            archive.restore(file)
            assertEquals(20000L, progress().saved.cents)
            assertEquals(layout, wealth.observeCards("l1").first())
            assertEquals(2, wealth.observeBaselines("l1").first().size)
            assertEquals(1, wealth.links(goal).size)
        } finally { file.delete() }
    }

    @Test fun versionThreeBackupWithoutWealthTablesRestoresOldLedgerAndBudgets() = runTest {
        seed()
        val archive = SnapshotArchive(context, db)
        val json = File(context.cacheDir, "wealth-old-${System.nanoTime()}.json")
        val file = File(context.cacheDir, "wealth-old-${System.nanoTime()}.zip")
        try {
            archive.exportDatabaseJson(json)
            val doc = JSONObject(json.readText()).put("schemaVersion", 3)
            val tables = doc.getJSONObject("tables")
            SnapshotArchive.TABLES.filter { SnapshotArchive.tableVersion(it) == 4 }.forEach(tables::remove)
            val bytes = doc.toString().toByteArray()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val counts = JSONObject(); tables.keys().forEach { counts.put(it, tables.getJSONArray(it).length()) }
            val manifest = JSONObject().put("formatVersion", 3).put("createdAt", 1).put("counts", counts).put("sha256OfDb", hash)
            ZipOutputStream(file.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.json")); out.write(manifest.toString().toByteArray()); out.closeEntry()
                out.putNextEntry(ZipEntry("db.json")); out.write(bytes); out.closeEntry()
            }
            archive.restore(file)
            assertEquals(2, db.ledgerDao().count())
            assertEquals(100000L, accounts.getById("cash")!!.currentBalance.cents)
            assertTrue(wealth.observeGoals("l1").first().isEmpty())
            assertEquals(HomeCardLayout.defaults(), HomeCardLayout.normalize(wealth.observeCards("l1").first()))
            wealth.ensureBaselines(accounts.listByLedger("l1"), 1000).getOrThrow()
            assertEquals(2, wealth.observeBaselines("l1").first().size)
        } finally { file.delete(); json.delete() }
    }
}
