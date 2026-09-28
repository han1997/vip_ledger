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

    /**
     * PRD 验收标准 4：真实微信账单（含「起始时间/终止时间」前导行）导入两次，
     * 第二次全部判重、库里不得多出一条。断言落在数据库行数上，不只看报告数字。
     */
    @Test
    fun realisticWeChatBillInsertsOnceThenEveryRowIsDuplicate() = runTest {
        seed()
        val manager = manager(AccountBalanceSyncerImpl(db.accountDao(), db.txnDao()))
        val mapping = CsvLedgerImporter.presetMapping(WECHAT_HEADER, "wechat")

        val first = manager.importCsv("l1", "a1", WECHAT_BILL, mapping, "wechat")
        assertEquals(5, first.inserted, "转账行以外的 5 条应全部入库")
        assertEquals(0, first.duplicates)
        assertEquals(listOf(12), first.failedLines, "转账行应作为失败行报告")
        assertEquals(5, db.txnDao().countByLedger("l1"), "首次导入后库里应有 5 条")

        val second = manager.importCsv("l1", "a1", WECHAT_BILL, mapping, "wechat")
        assertEquals(0, second.inserted)
        assertEquals(5, second.duplicates, "同一文件二次导入应逐行判重")
        assertEquals(listOf(12), second.failedLines)
        assertEquals(5, db.txnDao().countByLedger("l1"), "二次导入不得新增任何记录")
    }

    private companion object {
        val HEADERS = listOf("交易时间", "金额", "商品")
        const val CSV = "交易时间,金额,商品\r\n" +
            "2026-09-01 10:00,12.50,咖啡\r\n" +
            "2026-09-01 11:30,8.00,面包\r\n"

        val WECHAT_HEADER = listOf(
            "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
            "支付方式", "当前状态", "交易单号", "商户单号", "备注",
        )

        const val WECHAT_BILL = "微信支付账单明细\r\n" +
            "微信昵称：[测试用户]\r\n" +
            "起始时间：[2026-09-01 00:00:00] 终止时间：[2026-09-30 23:59:59]\r\n" +
            "导出类型：[全部]\r\n" +
            "导出时间：[2026-10-01 10:00:00]\r\n" +
            "\r\n" +
            "微信支付账单明细列表\r\n" +
            "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注\r\n" +
            "2026-09-01 08:15:00,商户消费,早餐店,豆浆油条,支出,12.50,零钱,支付成功,1001,2001,\r\n" +
            "2026-09-02 12:30:00,商户消费,公司食堂,午餐,支出,23.80,零钱,支付成功,1002,2002,\r\n" +
            "2026-09-03 19:05:00,商户消费,超市,日用百货,支出,66.00,零钱通,支付成功,1003,2003,周末采购\r\n" +
            "2026-09-05 09:00:00,转账,同事小李,转账,支出,100.00,零钱,支付成功,1004,2004,\r\n" +
            "2026-09-08 21:45:00,商户消费,奶茶店,珍珠奶茶,支出,18.00,零钱,支付成功,1005,2005,\r\n" +
            "2026-09-10 07:20:00,商户消费,便利店,面包牛奶,支出,9.90,零钱,支付成功,1006,2006,\r\n"
    }
}
