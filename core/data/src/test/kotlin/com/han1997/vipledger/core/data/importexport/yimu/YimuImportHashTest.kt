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
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportPreview
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportReport
import com.han1997.vipledger.core.domain.usecase.importbill.ImportTarget
import com.han1997.vipledger.core.domain.usecase.ledger.CreateLedgerUseCase
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.runner.RunWith

/**
 * importHash 口径(R7):分钟粒度 + 文件内重复行 `|#n` 后缀。
 *
 * 直接用真实解析出的 40 行夹具验证「键 → 哈希」的契约:同一键的多条流水哈希必须两两不同,
 * 分别等于 `sha1(key)`、`sha1(key|#2)`…;同一文件二次导入全部命中、不新增。
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTime::class)
class YimuImportHashTest {

    private lateinit var context: Context
    private lateinit var db: LedgerDatabase

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

    private fun importer() = YimuLedgerImporter(
        database = db,
        txnDao = db.txnDao(),
        accountDao = db.accountDao(),
        categoryDao = db.categoryDao(),
        tagDao = db.tagDao(),
        balanceSyncer = AccountBalanceSyncerImpl(db.accountDao(), db.txnDao()),
        createLedger = CreateLedgerUseCase(
            ledgerRepository = LedgerRepositoryImpl(db.ledgerDao(), timeProvider),
            accountRepository = AccountRepositoryImpl(db.accountDao()),
            categoryRepository = CategoryRepositoryImpl(db.categoryDao()),
            builtinCategorySource = ResourceBuiltinCategorySource(context),
            transactionRunner = TransactionRunnerImpl(db),
            timeProvider = timeProvider,
            idGenerator = idGenerator,
        ),
        builtinCategorySource = ResourceBuiltinCategorySource(context),
        timeProvider = timeProvider,
        idGenerator = idGenerator,
    )

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun key(txn: TxnEntity): String =
        "${txn.occurredAt / 60_000}|${txn.amountCents}|${txn.accountId}|${txn.title}"

    private suspend fun importFixture(): Pair<BillImportReport, List<TxnEntity>> {
        val importer = importer()
        val target = ImportTarget.newLedger("一木记账")
        val preview = assertIs<AppResult.Success<BillImportPreview>>(
            importer.inspect(fixtureBytes(), target),
        ).value
        val report = assertIs<AppResult.Success<BillImportReport>>(importer.commit(preview, target)).value
        return report to db.txnDao().listByLedger(report.ledgerId, includeDeleted = true)
    }

    @Test
    fun inFileDuplicateKeysGetDistinctSuffixedHashes() = runTest {
        val (_, txns) = importFixture()
        assertEquals(40, txns.size)

        val byKey = txns.groupBy { key(it) }
        byKey.forEach { (groupKey, group) ->
            val expected = group.indices.map { index ->
                if (index == 0) sha1(groupKey) else sha1("$groupKey|#${index + 1}")
            }.toSet()
            assertEquals(expected, group.map { it.importHash }.toSet(), "键 $groupKey 的哈希序列不符")
        }
        // 夹具里有两组键完全重复(16:02 与 16:00 的 -0.01 慧生活798)。
        val duplicatedGroups = byKey.values.filter { it.size >= 2 }
        assertEquals(2, duplicatedGroups.size, "夹具应有两组文件内重复键")
        assertTrue(duplicatedGroups.all { it.size == 2 })
    }

    @Test
    fun minuteGranularityBucketIsUsedInTheKey() = runTest {
        val (_, txns) = importFixture()
        // 重复组的两条必须落同一分钟桶(证明键是分钟粒度,不是秒)。
        val group = txns.filter { it.title == "慧生活798" && it.amountCents == 1L }
            .groupBy { key(it) }
            .values
            .first { it.size == 2 }
        assertEquals(1, group.map { it.occurredAt / 60_000 }.toSet().size, "同键两条应落同一分钟桶")
        // 但毫秒不同:16:02:xx 的两条记录时间戳不同,分钟桶相同。
        assertEquals(2, group.map { it.importHash }.toSet().size, "分钟桶相同也要靠 |#n 区分")
    }

    @Test
    fun secondRunHitsEveryExistingHash() = runTest {
        val (report, txns) = importFixture()
        val allHashes = txns.map { it.importHash }
        assertEquals(40, allHashes.size)
        allHashes.forEach { hash ->
            assertTrue(hash != null && db.txnDao().existsImportHash(hash), "首轮写入的每条哈希都应可被查到")
        }

        // 二次导入同一文件全部命中。
        val importer = importer()
        val target = ImportTarget.existing(report.ledgerId)
        val preview = assertIs<AppResult.Success<BillImportPreview>>(
            importer.inspect(fixtureBytes(), target),
        ).value
        val second = assertIs<AppResult.Success<BillImportReport>>(importer.commit(preview, target)).value
        assertEquals(0, second.inserted)
        assertEquals(40, second.duplicates)
    }
}