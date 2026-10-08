package com.han1997.vipledger.core.database

import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.CategoryEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.database.entity.TagEntity
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.database.entity.TxnTagEntity
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerDatabaseTest {

    private lateinit var db: LedgerDatabase

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun ledger(id: String = "l1", name: String = "默认账本", isDefault: Boolean = true) = LedgerEntity(
        id = id,
        name = name,
        isDefault = isDefault,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun account(id: String, name: String, type: AccountType = AccountType.CASH, ledgerId: String = "l1") = AccountEntity(
        id = id,
        ledgerId = ledgerId,
        name = name,
        type = type,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun category(id: String, name: String, parentId: String? = null, kind: CategoryKind = CategoryKind.EXPENSE) =
        CategoryEntity(
            id = id,
            ledgerId = "l1",
            parentId = parentId,
            name = name,
            kind = kind,
            createdAt = 1,
            updatedAt = 1,
        )

    private fun tag(id: String, name: String) = TagEntity(id = id, ledgerId = "l1", name = name, createdAt = 1, updatedAt = 1)

    private fun txn(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        amountCents: Long = 1_000,
        accountId: String = "cash",
        toAccountId: String? = null,
        categoryId: String? = null,
        occurredAt: Long = DAY_2,
        title: String = "",
        remark: String = "",
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
        importHash: String? = null,
        deletedAt: Long? = null,
    ) = TxnEntity(
        id = id,
        ledgerId = "l1",
        type = type,
        amountCents = amountCents,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        occurredAt = occurredAt,
        title = title,
        remark = remark,
        draftStatus = draftStatus,
        importHash = importHash,
        createdAt = 1,
        updatedAt = 1,
        deletedAt = deletedAt,
    )

    private suspend fun seedWorld() {
        db.ledgerDao().upsert(ledger())
        db.accountDao().upsert(account("cash", "现金"))
        db.accountDao().upsert(account("card", "信用卡", AccountType.CREDIT_CARD))
        db.categoryDao().upsertAll(
            listOf(
                category("food", "餐饮"),
                category("breakfast", "早餐", parentId = "food"),
                category("salary", "工资", kind = CategoryKind.INCOME),
            ),
        )
        db.tagDao().upsert(tag("trip", "出差"))
        db.tagDao().upsert(tag("reimburse", "可报销"))
    }

    private suspend fun filtered(
        includeDeleted: Boolean = false,
        from: Long? = null,
        to: Long? = null,
        keyword: String? = null,
        typeCsv: String? = null,
        categoryCsv: String? = null,
        accountCsv: String? = null,
        tagCsv: String? = null,
        minAmount: Long? = null,
        maxAmount: Long? = null,
        draftStatus: String? = null,
        limit: Int = -1,
    ) = db.txnDao().listFiltered(
        ledgerId = "l1",
        includeDeleted = includeDeleted,
        from = from,
        to = to,
        keyword = keyword,
        typeCsv = typeCsv,
        categoryCsv = categoryCsv,
        accountCsv = accountCsv,
        tagCsv = tagCsv,
        minAmount = minAmount,
        maxAmount = maxAmount,
        draftStatus = draftStatus,
        limit = limit,
    ).map { it.txn.id }

    @Test
    fun emptyDatabaseSupportsTheFullWriteReadPath() = runTest {
        seedWorld()
        db.txnDao().upsert(txn("tx1", categoryId = "food", title = "午饭", amountCents = 2_500))
        db.txnTagDao().insertAll(listOf(TxnTagEntity("tx1", "trip"), TxnTagEntity("tx1", "reimburse")))

        val loaded = assertNotNull(db.txnDao().getWithTags("tx1"))
        assertEquals(2_500L, loaded.txn.amountCents)
        assertEquals(setOf("trip", "reimburse"), loaded.tagLinks.map { it.tagId }.toSet())
        assertEquals("现金", db.accountDao().getById("cash")?.name)
        assertEquals(1, db.categoryDao().countChildren("food"))
        assertEquals(listOf("breakfast"), db.categoryDao().listByLedger("l1", includeArchived = false).filter { it.parentId != null }.map { it.id })
        assertTrue(db.ledgerDao().getDefault()?.isDefault == true)
    }

    @Test
    fun filtersCoverKeywordRangeCategoryTagAccountTypeAmountAndDraft() = runTest {
        seedWorld()
        db.txnDao().upsert(txn("tx1", categoryId = "food", occurredAt = DAY_1, title = "午饭", amountCents = 2_500))
        db.txnDao().upsert(txn("tx2", categoryId = "breakfast", occurredAt = DAY_2, remark = "豆浆油条", amountCents = 800))
        db.txnDao().upsert(txn("tx3", type = TransactionType.INCOME, categoryId = "salary", occurredAt = DAY_3, title = "工资", amountCents = 500_000))
        db.txnDao().upsert(txn("tx4", type = TransactionType.TRANSFER, accountId = "cash", toAccountId = "card", occurredAt = DAY_3, amountCents = 10_000))
        db.txnDao().upsert(txn("tx5", categoryId = "food", occurredAt = DAY_4, title = "待确认", draftStatus = DraftStatus.PENDING, amountCents = 9_900))
        db.txnTagDao().insertAll(listOf(TxnTagEntity("tx1", "trip"), TxnTagEntity("tx2", "trip")))

        val all = filtered()
        assertEquals(setOf("tx1", "tx2", "tx3", "tx4", "tx5"), all.toSet())
        // 默认按 occurred_at 倒序：最新的在最前，最早的在最后
        assertEquals("tx5", all.first())
        assertEquals("tx1", all.last())

        assertEquals(listOf("tx3"), filtered(typeCsv = ",INCOME,"))
        assertEquals(setOf("tx1", "tx2", "tx5"), filtered(categoryCsv = ",food,breakfast,").toSet())
        assertEquals(listOf("tx4"), filtered(accountCsv = ",card,"))
        assertEquals(setOf("tx1", "tx2"), filtered(tagCsv = ",trip,").toSet())
        assertEquals(listOf("tx2"), filtered(keyword = "油条"))
        assertEquals(listOf("tx2"), filtered(keyword = "豆浆"))
        assertEquals(listOf("tx3"), filtered(keyword = "工资"))
        assertEquals(listOf("tx3"), filtered(minAmount = 100_000))
        assertEquals(setOf("tx1", "tx2"), filtered(maxAmount = 2_500).toSet())
        assertEquals(listOf("tx5"), filtered(draftStatus = "PENDING"))
        assertEquals(setOf("tx3", "tx4"), filtered(from = DAY_3, to = DAY_4).toSet())
        assertEquals(setOf("tx1", "tx2"), filtered(from = DAY_1, to = DAY_3).toSet())
        assertEquals(listOf<String>(), filtered(from = DAY_4 + 86_400_000L))
        assertEquals(4, filtered(limit = 4).size)
    }

    @Test
    fun softDeletedTxnsAreHiddenUnlessExplicitlyRequested() = runTest {
        seedWorld()
        db.txnDao().upsert(txn("tx1"))
        db.txnDao().upsert(txn("tx2"))
        db.txnDao().softDelete("tx2", deletedAt = 99)

        assertEquals(listOf("tx1"), filtered())
        assertEquals(setOf("tx1", "tx2"), filtered(includeDeleted = true).toSet())
        assertEquals(1, db.txnDao().countByLedger("l1"))

        db.txnDao().restore("tx2", updatedAt = 100)
        assertEquals(setOf("tx1", "tx2"), filtered().toSet())
    }

    @Test
    fun importHashDeduplicatesAndToleratesNulls() = runTest {
        seedWorld()
        db.txnDao().upsert(txn("tx1", importHash = "hash-a"))
        db.txnDao().upsert(txn("tx2"))
        db.txnDao().upsert(txn("tx3"))
        assertEquals(setOf("tx1", "tx2", "tx3"), filtered().toSet())
        assertTrue(db.txnDao().existsImportHash("hash-a"))
        assertTrue(!db.txnDao().existsImportHash("hash-b"))

        db.txnDao().upsert(txn("tx4", importHash = "hash-a"))
        assertEquals(setOf("tx2", "tx3", "tx4"), filtered().toSet())
    }

    @Test
    fun accountBalanceCacheAndArchiveFlagsAreUpdatedInPlace() = runTest {
        seedWorld()
        db.accountDao().updateBalance("cash", 123_456, updatedAt = 7)
        assertEquals(123_456L, db.accountDao().getById("cash")?.currentBalanceCents)
        assertEquals(7L, db.accountDao().getById("cash")?.updatedAt)

        db.accountDao().setArchived("card", archived = true, updatedAt = 8)
        assertEquals(listOf("cash"), db.accountDao().listByLedger("l1", includeArchived = false).map { it.id })
        assertEquals(setOf("cash", "card"), db.accountDao().listByLedger("l1", includeArchived = true).map { it.id }.toSet())
    }

    @Test
    fun ledgerDefaultFlagIsExclusive() = runTest {
        db.ledgerDao().upsert(ledger(id = "l1", name = "默认账本", isDefault = true))
        db.ledgerDao().upsert(ledger(id = "l2", name = "旅行账本", isDefault = false))
        db.ledgerDao().setDefault("l2", updatedAt = 5)
        assertEquals("l2", db.ledgerDao().getDefault()?.id)
        assertEquals(1, db.ledgerDao().listAll(includeArchived = true).count { it.isDefault })
        assertEquals(2, db.ledgerDao().count())
    }

    @Test
    fun foreignKeysCascadeFromLedgerAndTxn() = runTest {
        seedWorld()
        db.txnDao().upsert(txn("tx1"))
        db.txnTagDao().insertAll(listOf(TxnTagEntity("tx1", "trip")))
        assertEquals(1, db.txnTagDao().listByTagId("trip").size)

        db.txnDao().deleteHard("tx1")
        assertEquals(0, db.txnTagDao().listByTagId("trip").size)
        assertNull(db.txnDao().getById("tx1"))
    }

    @Test
    fun observingTheTxnFilterEmitsCurrentRows() = runTest {
        seedWorld()
        db.txnDao().upsert(txn("tx1", title = "午饭"))
        val ids = db.txnDao().observeFiltered(
            ledgerId = "l1",
            includeDeleted = false,
            from = null,
            to = null,
            keyword = null,
            typeCsv = null,
            categoryCsv = null,
            accountCsv = null,
            tagCsv = null,
            minAmount = null,
            maxAmount = null,
            draftStatus = null,
            limit = -1,
        ).first().map { it.txn.id }
        assertEquals(listOf("tx1"), ids)
    }

    @Test
    fun schemaContainsEveryDocumentedTable() {
        val tables = queryStrings("SELECT name FROM sqlite_master WHERE type = 'table'").toSet()
        assertEquals(
            setOf(
                "ledger",
                "account",
                "category",
                "tag",
                "txn_tag",
                "txn",
                "recurring_rule",
                "installment_plan",
                "installment_period",
                "merchant_rule",
                "attachment",
                "ml_training_sample",
                "auto_config", "auto_binding", "auto_candidate", "auto_evidence",
            ),
            tables - setOf("room_master_table", "android_metadata", "sqlite_sequence"),
        )
    }

    @Test
    fun schemaKeepsTheIndexesTheDocsPromise() {
        val txnIndices = queryStrings("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'txn'").toSet()
        listOf(
            "index_txn_ledger_id_occurred_at",
            "index_txn_ledger_id_category_id_occurred_at",
            "index_txn_ledger_id_type_occurred_at",
            "index_txn_account_id_occurred_at",
            "index_txn_to_account_id",
            "index_txn_recurring_rule_id",
            "index_txn_installment_plan_id_installment_period_no",
            "index_txn_ledger_id_draft_status",
            "index_txn_import_hash",
        ).forEach { assertTrue(it in txnIndices, "缺少索引 $it") }
    }

    private fun queryStrings(sql: String): List<String> {
        val cursor: Cursor = db.openHelper.writableDatabase.query(sql)
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(it.getString(0))
            }
        }
    }

    private companion object {
        const val DAY_1 = 1_790_208_000_000L
        const val DAY_2 = DAY_1 + 86_400_000L
        const val DAY_3 = DAY_2 + 86_400_000L
        const val DAY_4 = DAY_3 + 86_400_000L
    }
}
