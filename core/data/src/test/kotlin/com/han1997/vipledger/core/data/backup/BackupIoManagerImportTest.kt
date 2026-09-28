package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.importexport.CsvLedgerImporter
import com.han1997.vipledger.core.data.support.AccountBalanceSyncerImpl
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.usecase.backup.BuildBackupCsvUseCase
import com.han1997.vipledger.core.model.AccountType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.runner.RunWith

/**
 * 导入的去重与事务原子性（`.trellis/spec/android/import-export.md` 导入一节）：
 * 同一文件二次导入全部判重、不新增；中途失败整批回滚、不留半份数据。
 */
@RunWith(AndroidJUnit4::class)
class BackupIoManagerImportTest {

    private lateinit var context: Context
    private lateinit var db: LedgerDatabase

    private val timeProvider = object : TimeProvider {
        override fun nowMillis(): Long = 1_790_208_000_000L
        override val zone: TimeZone = TimeZone.of("Asia/Shanghai")
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

    private suspend fun seed() {
        db.ledgerDao().upsert(LedgerEntity(id = "l1", name = "默认账本", isDefault = true, createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(
            AccountEntity(id = "a1", ledgerId = "l1", name = "现金", type = AccountType.CASH, createdAt = 1, updatedAt = 1),
        )
    }

    private fun manager(syncer: AccountBalanceSyncer): BackupIoManager = BackupIoManager(
        context = context,
        database = db,
        txnDao = db.txnDao(),
        accountDao = db.accountDao(),
        categoryDao = db.categoryDao(),
        tagDao = db.tagDao(),
        balanceSyncer = syncer,
        buildBackupCsv = BuildBackupCsvUseCase(timeProvider),
    )

    private val mapping get() = CsvLedgerImporter.presetMapping(HEADERS, "wechat")

    @Test
    fun secondImportOfTheSameFileIsAllDuplicatesAndInsertsNothing() = runTest {
        seed()
        val manager = manager(AccountBalanceSyncerImpl(db.accountDao(), db.txnDao()))

        val first = manager.importCsv("l1", "a1", CSV, mapping, "wechat")
        assertEquals(2, first.inserted)
        assertEquals(0, first.duplicates)

        val second = manager.importCsv("l1", "a1", CSV, mapping, "wechat")
        assertEquals(0, second.inserted)
        assertEquals(2, second.duplicates)
        assertEquals(2, db.txnDao().countByLedger("l1"), "二次导入不得留下额外记录")
    }

    @Test
    fun midBatchFailureRollsBackTheWholeBatch() = runTest {
        seed()
        val throwingSyncer = object : AccountBalanceSyncer {
            override suspend fun syncBalances(accountIds: Collection<String>, updatedAt: Long) {
                throw IllegalStateException("balance sync boom")
            }
        }
        val manager = manager(throwingSyncer)

        val report = manager.importCsv("l1", "a1", CSV, mapping, "wechat")

        assertEquals(0, report.inserted)
        assertEquals(0, report.duplicates)
        assertEquals(listOf(2, 3), report.failedLines, "整批回滚时所有已解析行都要出现在失败报告里")
        assertEquals(0, db.txnDao().countByLedger("l1"), "失败不得留下半份数据")
    }

    private companion object {
        val HEADERS = listOf("交易时间", "金额", "商品")
        const val CSV = "交易时间,金额,商品\r\n" +
            "2026-09-01 10:00,12.50,咖啡\r\n" +
            "2026-09-01 11:30,8.00,面包\r\n"
    }
}
