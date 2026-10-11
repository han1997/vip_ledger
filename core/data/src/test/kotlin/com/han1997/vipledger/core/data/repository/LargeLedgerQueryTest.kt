package com.han1997.vipledger.core.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.TxnSource
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 十万流水规模的数据层查询回归（P8 贯通验收）。
 *
 * 内存库单事务插入 10 万条账目后，验证关键查询路径（列表过滤、类型过滤、区间过滤、计数）
 * 的结果正确性，并对每条查询给宽松时间上限——只兜住「意外全表扫描」级退化，
 * 不追求精确基准。滚动体验是 UI 行为，由真机观察（见 docs/ACCEPTANCE-RECORD.md）。
 */
@RunWith(AndroidJUnit4::class)
class LargeLedgerQueryTest {

    private companion object {
        const val TOTAL = 100_000
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val FIRST_DAY_EPOCH = 20_000L
        const val TIME_BUDGET_MS = 10_000L
    }

    private lateinit var db: LedgerDatabase

    @BeforeTest
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LedgerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun entity(index: Int): TxnEntity {
        val dayEpoch = FIRST_DAY_EPOCH + index / 100
        val type = when (index % 3) {
            0 -> TransactionType.EXPENSE
            1 -> TransactionType.TRANSFER
            else -> TransactionType.INCOME
        }
        return TxnEntity(
            id = "t$index",
            ledgerId = "l",
            type = type,
            amountCents = (index % 500 + 1) * 100L,
            accountId = "a",
            occurredAt = dayEpoch * DAY_MILLIS + (index % 1000) * 1000L,
            title = "txn$index",
            source = TxnSource.MANUAL,
            draftStatus = if (index % 97 == 0) DraftStatus.PENDING else DraftStatus.CONFIRMED,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = if (index % 101 == 0) 9L else null,
        )
    }

    @Test
    fun hundredThousandTxnQueriesStayFastAndCorrect() = runTest {
        db.ledgerDao().upsert(LedgerEntity("l", "Ledger", createdAt = 1, updatedAt = 1))
        db.accountDao().upsert(
            AccountEntity("a", "l", "Wallet", com.han1997.vipledger.core.model.AccountType.VIRTUAL, createdAt = 1, updatedAt = 1),
        )

        // 生成期同步统计期望值（与 SQL 语义一致：deleted_at 排除、草稿保留在列表里）。
        var expectedNonDeleted = 0
        var expectedExpenseRows = 0
        var expectedRangeRows = 0 // 前 500 天（to 为开区间 → i < 50000）
        val entities = ArrayList<TxnEntity>(TOTAL)
        for (i in 0 until TOTAL) {
            val e = entity(i)
            entities += e
            if (e.deletedAt == null) {
                expectedNonDeleted++
                if (e.type == TransactionType.EXPENSE) expectedExpenseRows++
                if (i < 50_000) expectedRangeRows++
            }
        }

        var started = System.nanoTime()
        db.withTransaction { entities.forEach { db.txnDao().upsert(it) } }
        val insertMs = (System.nanoTime() - started) / 1_000_000

        // 计数（不含已删除）。
        started = System.nanoTime()
        assertEquals(expectedNonDeleted, db.txnDao().countByLedger("l"))
        val countMs = (System.nanoTime() - started) / 1_000_000

        // 列表默认过滤：全量非删除、时间倒序（99999 % 101 != 0 → 未删除，最新一条在最前）。
        // 全量物化（含标签关系映射）只验正确性：时间噪声大，不是应用的真实查询路径。
        started = System.nanoTime()
        val all = db.txnDao().listFiltered(
            ledgerId = "l", includeDeleted = false, from = null, to = null,
            keyword = null, typeCsv = null, categoryCsv = null, accountCsv = null,
            tagCsv = null, minAmount = null, maxAmount = null, draftStatus = null,
            limit = Int.MAX_VALUE,
        )
        val listMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(expectedNonDeleted, all.size)
        assertEquals("t99999", all.first().txn.id)
        assertTrue(all.first().txn.occurredAt >= all.last().txn.occurredAt, "按时间倒序")
        assertTrue(listMs < 60_000, "全量 list 耗时 ${listMs}ms，出现四则级退化")

        // 应用真实路径是限量分页查询：时间契约设在限量变体上。
        started = System.nanoTime()
        val firstPage = db.txnDao().listFiltered(
            ledgerId = "l", includeDeleted = false, from = null, to = null,
            keyword = null, typeCsv = null, categoryCsv = null, accountCsv = null,
            tagCsv = null, minAmount = null, maxAmount = null, draftStatus = null,
            limit = 100,
        )
        val pageMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(100, firstPage.size)
        assertEquals("t99999", firstPage.first().txn.id)
        assertTrue(pageMs < TIME_BUDGET_MS, "限量列表耗时 ${pageMs}ms，出现退化")

        // 类型过滤（typeCsv 必须是「,TYPE,」包裹格式，见 TXN_FILTER_SQL 的 INSTR 契约）；
        // 结果集大（3 万+），只验正确性；时间契约看上面的限量查询。
        val expenses = db.txnDao().listFiltered(
            ledgerId = "l", includeDeleted = false, from = null, to = null,
            keyword = null, typeCsv = ",EXPENSE,", categoryCsv = null, accountCsv = null,
            tagCsv = null, minAmount = null, maxAmount = null, draftStatus = null,
            limit = Int.MAX_VALUE,
        )
        assertEquals(expectedExpenseRows, expenses.size)
        assertTrue(expenses.all { it.txn.type == TransactionType.EXPENSE })

        // 区间过滤（to 为开区间，见 TXN_FILTER_SQL）；同样只验正确性。
        val ranged = db.txnDao().listFiltered(
            ledgerId = "l", includeDeleted = false,
            from = FIRST_DAY_EPOCH * DAY_MILLIS,
            to = (FIRST_DAY_EPOCH + 500) * DAY_MILLIS,
            keyword = null, typeCsv = null, categoryCsv = null, accountCsv = null,
            tagCsv = null, minAmount = null, maxAmount = null, draftStatus = null,
            limit = Int.MAX_VALUE,
        )
        assertEquals(expectedRangeRows, ranged.size)

        assertTrue(countMs < TIME_BUDGET_MS, "count 耗时 ${countMs}ms")
        assertTrue(pageMs < TIME_BUDGET_MS, "限量列表耗时 ${pageMs}ms")
        assertTrue(insertMs < 60_000, "10 万条插入耗时 ${insertMs}ms，超出测试可接受范围")
    }
}