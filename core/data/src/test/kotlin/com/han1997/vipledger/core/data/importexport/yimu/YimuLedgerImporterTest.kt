package com.han1997.vipledger.core.data.importexport.yimu

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.builtin.ResourceBuiltinCategorySource
import com.han1997.vipledger.core.data.repository.AccountRepositoryImpl
import com.han1997.vipledger.core.data.repository.CategoryRepositoryImpl
import com.han1997.vipledger.core.data.repository.LedgerRepositoryImpl
import com.han1997.vipledger.core.data.support.AccountBalanceSyncerImpl
import com.han1997.vipledger.core.data.support.TransactionRunnerImpl
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportPreview
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportReport
import com.han1997.vipledger.core.domain.usecase.importbill.ImportTarget
import com.han1997.vipledger.core.domain.usecase.ledger.CreateLedgerUseCase
import com.han1997.vipledger.core.model.AccountType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.runner.RunWith

/**
 * 一木账单导入的端到端回归(脱敏 40 行夹具,`core/data/src/test/resources/yimu_fixture_40.xls`,D11)。
 *
 * 覆盖 R4–R8:分类/账户映射落库、金额合计、importHash 幂等、事务整体回滚。
 * 全量 6008 行的回归用 `YIMU_SAMPLE_XLS` 环境变量门控,只在本地跑(见文件末尾)。
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTime::class)
class YimuLedgerImporterTest {

    private lateinit var context: Context
    private lateinit var db: LedgerDatabase

    private val testZone = TimeZone.of("Asia/Shanghai")

    private val timeProvider = object : TimeProvider {
        override fun nowMillis(): Long = 1_790_208_000_000L
        override val zone: TimeZone = TimeZone.of("Asia/Shanghai")
    }

    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "id-${counter++}"
    }

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun fixtureBytes(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("yimu_fixture_40.xls")!!.readBytes()

    private fun createLedgerUseCase(): CreateLedgerUseCase = CreateLedgerUseCase(
        ledgerRepository = LedgerRepositoryImpl(db.ledgerDao(), timeProvider),
        accountRepository = AccountRepositoryImpl(db.accountDao()),
        categoryRepository = CategoryRepositoryImpl(db.categoryDao()),
        builtinCategorySource = ResourceBuiltinCategorySource(context),
        transactionRunner = TransactionRunnerImpl(db),
        timeProvider = timeProvider,
        idGenerator = idGenerator,
    )

    private fun importer(syncer: AccountBalanceSyncer): YimuLedgerImporter = YimuLedgerImporter(
        database = db,
        txnDao = db.txnDao(),
        accountDao = db.accountDao(),
        categoryDao = db.categoryDao(),
        tagDao = db.tagDao(),
        balanceSyncer = syncer,
        createLedger = createLedgerUseCase(),
        builtinCategorySource = ResourceBuiltinCategorySource(context),
        timeProvider = timeProvider,
        idGenerator = idGenerator,
    )

    private fun realImporter() = importer(AccountBalanceSyncerImpl(db.accountDao(), db.txnDao()))

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(testZone).toEpochMilliseconds()

    // ---- inspect:预览口径 ----

    @Test
    fun inspectBuildsDomainPreviewFromFixture() = runTest {
        val result = realImporter().inspect(fixtureBytes(), ImportTarget.newLedger("一木记账"))
        val preview = assertIs<AppResult.Success<BillImportPreview>>(result).value

        assertEquals(40, preview.dataRowCount)
        assertEquals(0, preview.skippedRows.size)
        assertEquals(0, preview.failedRows.size)
        // 支出 3264.94,收入 2.00,净额 -3262.94(与 xlrd 独立核对一致)。
        assertEquals(326_494L, preview.expenseCents)
        assertEquals(200L, preview.incomeCents)
        assertEquals(-326_294L, preview.netCents)
        assertEquals(millis(2026, 10, 3, 9, 19), preview.startAt)
        assertEquals(millis(2026, 10, 8, 20, 48), preview.endAt)
        // 将新建:7 个二级 + 7 个账户。含 居家生活/话费宽带→「话费」在内置父级「居住」下新建 ——
        // 内置「话费」挂在「通讯」下,与映射目标父级「居住」不同,不做跨父级复用(见 import-export.md 一木一节 R4)。
        assertEquals(7, preview.newCategories.size)
        assertEquals(7, preview.newAccounts.size)
        // 首行映射:2026-10-08 20:48 / 支出 0.07 / 餐饮·饮料酒水 / 支付宝。
        val first = preview.rows.first()
        assertEquals(2, first.sourceRow)
        assertEquals(millis(2026, 10, 8, 20, 48), first.occurredAt)
        assertEquals(7L, first.amountCents)
        assertEquals("餐饮", first.categoryParentName)
        assertEquals("饮料酒水", first.categoryChildName)
        assertEquals("支付宝", first.accountName)
        assertEquals("慧生活798", first.title)
    }

    // ---- commit:落库 + 幂等 ----

    @Test
    fun commitIntoNewLedgerInsertsAllRowsAndBalanceMatchesNet() = runTest {
        val importer = realImporter()
        val target = ImportTarget.newLedger("一木记账")
        val preview = assertIs<AppResult.Success<BillImportPreview>>(
            importer.inspect(fixtureBytes(), target),
        ).value

        var progressTotal = 0
        var progressDone = 0
        val report = assertIs<AppResult.Success<BillImportReport>>(
            importer.commit(preview, target) { done, total -> progressDone = done; progressTotal = total },
        ).value

        assertEquals(40, report.inserted)
        assertEquals(0, report.duplicates)
        assertEquals(0, report.failedRows.size)
        assertEquals(7, report.newCategoryCount)
        assertEquals(7, report.newAccountCount)
        assertEquals(40, progressTotal)
        assertEquals(40, progressDone)

        assertEquals(40, db.txnDao().countByLedger(report.ledgerId))
        // 账户余额合计 == 净额(initialBalance 恒 0,余额 = Σdelta)。
        val totalBalance = db.accountDao().listByLedger(report.ledgerId, includeArchived = true)
            .sumOf { it.currentBalanceCents }
        assertEquals(-326_294L, totalBalance)
    }

    @Test
    fun secondImportOfSameFileIsAllDuplicatesAndInsertsNothing() = runTest {
        val importer = realImporter()
        val target = ImportTarget.newLedger("一木记账")
        val preview = assertIs<AppResult.Success<BillImportPreview>>(
            importer.inspect(fixtureBytes(), target),
        ).value

        val first = assertIs<AppResult.Success<BillImportReport>>(importer.commit(preview, target)).value
        assertEquals(40, first.inserted)

        // 第二次导入同一文件:全部命中已有 import_hash(含文件内重复行的 `|#n` 后缀)。
        // 落点换成上一轮建出来的账本,否则 newLedger 目标会再次新建同名账本、被 CreateLedgerUseCase 挡下。
        val reopened = ImportTarget.existing(first.ledgerId)
        val second = assertIs<AppResult.Success<BillImportReport>>(importer.commit(preview, reopened)).value
        assertEquals(0, second.inserted)
        assertEquals(40, second.duplicates)
        assertEquals(40, db.txnDao().countByLedger(first.ledgerId), "二次导入不得多出一条")
    }

    @Test
    fun inFileDuplicateRowsAreAllKeptOnFirstImport() = runTest {
        // 夹具里第 4、5 条(0 基 3、4)与第 6、7 条键完全重复;D7 要求加 `|#n` 后缀全量保留。
        val importer = realImporter()
        val target = ImportTarget.newLedger("一木记账")
        val preview = assertIs<AppResult.Success<BillImportPreview>>(
            importer.inspect(fixtureBytes(), target),
        ).value
        val report = assertIs<AppResult.Success<BillImportReport>>(importer.commit(preview, target)).value
        assertEquals(40, report.inserted, "文件内重复行必须全部入库,不能被唯一索引撞掉")
    }

    // ---- 事务回滚 ----

    @Test
    fun failureMidBatchRollsBackEverything() = runTest {
        db.ledgerDao().upsert(LedgerEntity(id = "l1", name = "默认账本", isDefault = true, createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(
            AccountEntity(id = "a1", ledgerId = "l1", name = "现金", type = AccountType.CASH, createdAt = 1, updatedAt = 1),
        )
        val throwingSyncer = object : AccountBalanceSyncer {
            override suspend fun syncBalances(accountIds: Collection<String>, updatedAt: Long) {
                throw IllegalStateException("balance sync boom")
            }
        }
        val importer = importer(throwingSyncer)
        val target = ImportTarget.existing("l1")
        val preview = assertIs<AppResult.Success<BillImportPreview>>(
            importer.inspect(fixtureBytes(), target),
        ).value

        val result = importer.commit(preview, target)

        assertTrue(result is AppResult.Failure, "整批失败必须返回 Failure")
        assertEquals(0, db.txnDao().countByLedger("l1"), "失败不得留下半份流水")
        assertEquals(0, db.categoryDao().listByLedger("l1", includeArchived = true).size, "失败不得新建分类")
        assertEquals(1, db.accountDao().listByLedger("l1", includeArchived = true).size, "失败不得新建账户")
    }

    // ---- 全量 6008 行:只在本地跑(YIMU_SAMPLE_XLS 门控) ----

    private val samplePath: String? = System.getenv("YIMU_SAMPLE_XLS")

    @Test
    fun fullSampleInsertsThenIsIdempotent() = runTest {
        if (samplePath == null) return@runTest
        val importer = realImporter()
        val target = ImportTarget.newLedger("一木记账-全量")
        val preview = assertIs<AppResult.Success<BillImportPreview>>(
            importer.inspect(java.io.File(samplePath).readBytes(), target),
        ).value
        assertEquals(6008, preview.dataRowCount)
        assertEquals(0, preview.failedRows.size)

        val first = assertIs<AppResult.Success<BillImportReport>>(importer.commit(preview, target)).value
        assertEquals(6008, first.inserted + first.duplicates)
        assertEquals(0, first.failedRows.size)
        assertEquals(-412_342L, db.accountDao().listByLedger(first.ledgerId, includeArchived = true)
            .sumOf { it.currentBalanceCents })

        // 第二次导入同一文件:落点换成上一轮建出来的账本,否则 newLedger 目标会再次新建同名账本、
        // 被 CreateLedgerUseCase 的「同名拒绝」挡下并返回 Failure(与 :183 同型)。
        val reopened = ImportTarget.existing(first.ledgerId)
        val second = assertIs<AppResult.Success<BillImportReport>>(importer.commit(preview, reopened)).value
        assertEquals(0, second.inserted)
        assertEquals(6008, second.duplicates)
    }
}