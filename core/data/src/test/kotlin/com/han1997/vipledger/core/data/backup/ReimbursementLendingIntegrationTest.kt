package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.common.result.getOrThrow
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.repository.AccountRepositoryImpl
import com.han1997.vipledger.core.data.repository.CategoryRepositoryImpl
import com.han1997.vipledger.core.data.repository.LendingRepositoryImpl
import com.han1997.vipledger.core.data.repository.ReimbursementRepositoryImpl
import com.han1997.vipledger.core.data.repository.TxnRepositoryImpl
import com.han1997.vipledger.core.data.support.AccountBalanceSyncerImpl
import com.han1997.vipledger.core.data.support.TransactionRunnerImpl
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.CategoryEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.domain.calculator.StatsCalculator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.usecase.lending.CreateLendingUseCase
import com.han1997.vipledger.core.domain.usecase.lending.LendingTxnGuard
import com.han1997.vipledger.core.domain.usecase.lending.ObserveLendingsUseCase
import com.han1997.vipledger.core.domain.usecase.lending.RecordLendingInterestUseCase
import com.han1997.vipledger.core.domain.usecase.lending.SettleLendingUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ObserveReimbursementsUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ReceiveReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ReimbursementTxnGuard
import com.han1997.vipledger.core.domain.usecase.reimburse.SaveReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DeleteTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.UpdateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.json.JSONObject
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 报销与借贷跨越真实 Room、仓库与用例的集成回归（`.trellis/spec/android/assets-savings.md` 同类要求）。
 *
 * 覆盖：跨月到账口径、关联流水的改删守卫、关联插入失败整单回滚、
 * 完整快照恢复五张新表、以及旧格式（schema 4）缺新表时按空恢复。
 */
@RunWith(AndroidJUnit4::class)
class ReimbursementLendingIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: LedgerDatabase
    private lateinit var reimbursement: ReimbursementRepositoryImpl
    private lateinit var lending: LendingRepositoryImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var txns: TxnRepositoryImpl
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var runner: TransactionRunnerImpl
    private lateinit var syncer: AccountBalanceSyncerImpl
    private lateinit var create: CreateTxnUseCase
    private lateinit var observeReimbursements: ObserveReimbursementsUseCase
    private lateinit var saveProject: SaveReimbursementUseCase
    private lateinit var receive: ReceiveReimbursementUseCase
    private lateinit var createLending: CreateLendingUseCase
    private lateinit var settle: SettleLendingUseCase
    private lateinit var interest: RecordLendingInterestUseCase
    private lateinit var update: UpdateTxnUseCase
    private lateinit var delete: DeleteTxnUseCase

    private val zone = TimeZone.of("Asia/Shanghai")
    private val september = LocalDate(2026, 9, 1).startOfDayEpochMillis(zone)
    private val october = LocalDate(2026, 10, 1).startOfDayEpochMillis(zone)
    private val time = object : TimeProvider {
        override fun nowMillis() = october
        override val zone = TimeZone.of("Asia/Shanghai")
    }
    private val ids = object : IdGenerator { override fun newId() = UUID.randomUUID().toString() }

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).allowMainThreadQueries().build()
        reimbursement = ReimbursementRepositoryImpl(db)
        lending = LendingRepositoryImpl(db)
        accounts = AccountRepositoryImpl(db.accountDao())
        txns = TxnRepositoryImpl(db.txnDao(), time)
        categories = CategoryRepositoryImpl(db.categoryDao())
        runner = TransactionRunnerImpl(db)
        syncer = AccountBalanceSyncerImpl(db.accountDao(), db.txnDao())
        create = CreateTxnUseCase(txns, accounts, categories, runner, syncer, time, ids)
        observeReimbursements = ObserveReimbursementsUseCase(reimbursement, txns)
        saveProject = SaveReimbursementUseCase(reimbursement, txns, runner, time, ids)
        receive = ReceiveReimbursementUseCase(reimbursement, txns, accounts, create, runner)
        createLending = CreateLendingUseCase(lending, accounts, create, runner, time, ids)
        settle = SettleLendingUseCase(lending, txns, accounts, create, runner, time)
        interest = RecordLendingInterestUseCase(lending, accounts, create, runner, time)
        val reimbursementGuard = ReimbursementTxnGuard(reimbursement, txns)
        val lendingGuard = LendingTxnGuard(lending, txns)
        update = UpdateTxnUseCase(txns, accounts, categories, runner, syncer, time, reimbursementGuard = reimbursementGuard, lendingGuard = lendingGuard)
        delete = DeleteTxnUseCase(txns, runner, syncer, time, reimbursementGuard = reimbursementGuard, lendingGuard = lendingGuard)
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.ledgerDao().upsert(LedgerEntity(id = "l1", name = "One", createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(
            AccountEntity(
                id = "cash",
                ledgerId = "l1",
                name = "Cash",
                type = AccountType.CASH,
                initialBalanceCents = 100_000,
                currentBalanceCents = 100_000,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        db.categoryDao().upsert(
            CategoryEntity(id = "food", ledgerId = "l1", parentId = null, name = "差旅", kind = CategoryKind.EXPENSE, createdAt = 1, updatedAt = 1),
        )
    }

    private suspend fun expense(cents: Long, at: Long = september): Txn = create(
        TxnDraft(ledgerId = "l1", type = TransactionType.EXPENSE, amount = Money.ofCents(cents), accountId = "cash", categoryId = "food", occurredAt = at, title = "机票"),
    ).getOrThrow()

    private suspend fun newProject(itemId: String): String {
        assertTrue(saveProject(null, "l1", "出差报销", "公司", "", listOf(itemId)).isSuccess)
        return reimbursement.observeProjects("l1").first().single().id
    }

    private suspend fun newLending(principal: Long = 100_000): String = createLending(
        ledgerId = "l1",
        direction = LendingDirection.LEND,
        counterparty = "张三",
        principal = Money.ofCents(principal),
        cashAccountId = "cash",
        dueDate = null,
        remark = "",
        occurredAt = october,
        title = "借出",
    ).getOrThrow().id

    @Test
    fun crossMonthReceiptReducesItsMonthAndKeepsPendingClaim() = runTest {
        seed()
        val original = expense(100_000)
        val projectId = newProject(original.id)
        receive(projectId, original.id, Money.ofCents(60_000), "cash", october, "报销到账").getOrThrow()

        val ledgerTxns = txns.listByLedger("l1")
        val cats = categories.listByLedger("l1", includeArchived = true)
        val septemberSummary = StatsCalculator.summarize(ledgerTxns, cats, emptyList(), DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), zone)
        val octoberSummary = StatsCalculator.summarize(ledgerTxns, cats, emptyList(), DateRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31)), zone)
        assertEquals(Money.ofCents(100_000), septemberSummary.expense)
        assertEquals(Money.ofCents(-60_000), octoberSummary.expense)
        assertEquals(Money.ofCents(40_000), observeReimbursements("l1").first().single().pending)
    }

    @Test
    fun editsAndDeletesCannotDetachItemsReceiptsOrPrincipal() = runTest {
        seed()
        val original = expense(100_000)
        val projectId = newProject(original.id)
        val receipt = receive(projectId, original.id, Money.ofCents(60_000), "cash", october, "报销到账").getOrThrow()
        val lendingId = newLending()
        val disburse = txns.listByLedger("l1").first { it.type == TransactionType.TRANSFER && it.accountId == "cash" }

        assertFalse(delete(original.id).isSuccess)
        assertFalse(update(original.copy(amount = Money.ofCents(50_000))).isSuccess)
        assertFalse(update(receipt.copy(type = TransactionType.EXPENSE)).isSuccess)
        assertFalse(delete(disburse.id).isSuccess)
        assertFalse(update(disburse.copy(amount = Money.ofCents(50_000))).isSuccess)

        assertEquals(Money.ofCents(60_000), txns.getById(receipt.id)!!.amount)
        assertEquals(Money.ofCents(100_000), outstandingOf(lendingId))
    }

    private suspend fun outstandingOf(lendingId: String) =
        ObserveLendingsUseCase(lending, txns)("l1").first().first { it.record.id == lendingId }.outstanding

    @Test
    fun receiptLinkFailureRollsBackRefundTxnAndBalances() = runTest {
        seed()
        val original = expense(100_000)
        val projectId = newProject(original.id)
        val before = txns.countByLedger("l1")
        val cashBefore = accounts.getById("cash")!!.currentBalance.cents
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_receipt BEFORE INSERT ON reimbursement_receipt BEGIN SELECT RAISE(ABORT,'fixture'); END")

        assertFalse(receive(projectId, original.id, Money.ofCents(60_000), "cash", october, "报销到账").isSuccess)
        assertEquals(before, txns.countByLedger("l1"))
        assertEquals(cashBefore, accounts.getById("cash")!!.currentBalance.cents)
        assertTrue(reimbursement.receipts(projectId).isEmpty())
    }

    @Test
    fun lendingLinkFailureRollsBackAccountRecordAndTransferAtomically() = runTest {
        seed()
        val accountsBefore = accounts.listByLedger("l1", includeArchived = true).size
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_lending_link BEFORE INSERT ON lending_link BEGIN SELECT RAISE(ABORT,'fixture'); END")

        assertFalse(
            createLending("l1", LendingDirection.LEND, "张三", Money.ofCents(100_000), "cash", null, "", october, "借出").isSuccess,
        )
        assertEquals(accountsBefore, accounts.listByLedger("l1", includeArchived = true).size)
        assertTrue(lending.observeRecords("l1").first().isEmpty())
        assertEquals(0, txns.countByLedger("l1"))
        assertEquals(100_000L, accounts.getById("cash")!!.currentBalance.cents)
    }

    @Test
    fun fullSnapshotRestoresReimbursementAndLending() = runTest {
        seed()
        val original = expense(100_000)
        val projectId = newProject(original.id)
        receive(projectId, original.id, Money.ofCents(60_000), "cash", october, "报销到账").getOrThrow()
        val lendingId = newLending()
        settle(lendingId, "cash", Money.ofCents(40_000), october, "归还部分").getOrThrow()
        interest(lendingId, "cash", Money.ofCents(5_000), october, "收到利息").getOrThrow()

        val archive = SnapshotArchive(context, db)
        val file = File(context.cacheDir, "reimburse-lending-${System.nanoTime()}.zip")
        try {
            val manifest = archive.create(file)
            assertEquals(1, manifest.counts["reimbursement"])
            assertEquals(1, manifest.counts["lending"])
            SnapshotArchive.TABLES.asReversed().forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM `$it`") }
            assertTrue(reimbursement.observeProjects("l1").first().isEmpty())

            archive.restore(file)

            val progress = observeReimbursements("l1").first().single()
            assertEquals(Money.ofCents(60_000), progress.received)
            assertEquals(Money.ofCents(40_000), progress.pending)
            val lendProgress = ObserveLendingsUseCase(lending, txns)("l1").first().single()
            assertEquals(Money.ofCents(60_000), lendProgress.outstanding)
            assertEquals(Money.ofCents(5_000), lendProgress.interestTotal)
        } finally {
            file.delete()
        }
    }

    @Test
    fun versionFourBackupWithoutNewTablesRestoresOldLedger() = runTest {
        seed()
        val archive = SnapshotArchive(context, db)
        val json = File(context.cacheDir, "reimburse-old-${System.nanoTime()}.json")
        val file = File(context.cacheDir, "reimburse-old-${System.nanoTime()}.zip")
        try {
            archive.exportDatabaseJson(json)
            val doc = JSONObject(json.readText()).put("schemaVersion", 4)
            val tables = doc.getJSONObject("tables")
            SnapshotArchive.TABLES.filter { SnapshotArchive.tableVersion(it) == 5 }.forEach(tables::remove)
            val bytes = doc.toString().toByteArray()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val counts = JSONObject()
            tables.keys().forEach { counts.put(it, tables.getJSONArray(it).length()) }
            val manifest = JSONObject().put("formatVersion", 4).put("createdAt", 1).put("counts", counts).put("sha256OfDb", hash)
            ZipOutputStream(file.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.json")); out.write(manifest.toString().toByteArray()); out.closeEntry()
                out.putNextEntry(ZipEntry("db.json")); out.write(bytes); out.closeEntry()
            }

            archive.restore(file)

            assertEquals(1, db.ledgerDao().count())
            assertEquals(100_000L, accounts.getById("cash")!!.currentBalance.cents)
            assertTrue(reimbursement.observeProjects("l1").first().isEmpty())
            assertTrue(lending.observeRecords("l1").first().isEmpty())
        } finally {
            file.delete(); json.delete()
        }
    }
}
