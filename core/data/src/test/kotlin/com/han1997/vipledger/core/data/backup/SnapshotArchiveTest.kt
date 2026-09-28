package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.TransactionType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.runner.RunWith

/**
 * 快照创建/恢复的安全边界与往返保真度（`.trellis/spec/android/import-export.md`）。
 *
 * 覆盖：明文/加密往返、错误口令/被篡改的 db.json/未知格式版本都**不得触碰主库**、
 * BLOB 列的 JSON 往返无损。EncryptedSettingsStore 依赖 AndroidKeyStore，
 * Robolectric 下不可用，故不在这里断言（相关逻辑不涉及快照）。
 */
@RunWith(AndroidJUnit4::class)
class SnapshotArchiveTest {

    private lateinit var context: Context
    private lateinit var db: LedgerDatabase
    private lateinit var archive: SnapshotArchive

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        archive = SnapshotArchive(context, db)
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
        db.txnDao().upsert(
            TxnEntity(
                id = "tx1",
                ledgerId = "l1",
                type = TransactionType.EXPENSE,
                amountCents = 1_000,
                accountId = "a1",
                occurredAt = 1,
                title = "午饭",
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }

    private fun tempSnapshot(name: String): File = File(context.cacheDir, "$name-${System.nanoTime()}.zip")

    @Test
    fun plainRoundTripRestoresDeletedRows() = runTest {
        seed()
        val file = tempSnapshot("plain")
        val manifest = archive.create(file)
        try {
            assertEquals(1, manifest.counts["txn"])
            assertEquals(1, manifest.counts["account"])

            db.txnDao().deleteHard("tx1")
            assertEquals(0, db.txnDao().countByLedger("l1"))

            archive.restore(file)

            assertEquals(1, db.txnDao().countByLedger("l1"))
            assertEquals("午饭", db.txnDao().getById("tx1")?.title)
        } finally {
            file.delete()
        }
    }

    @Test
    fun encryptedRoundTripWithCorrectPassphraseSucceeds() = runTest {
        seed()
        val file = tempSnapshot("encrypted")
        archive.create(file, "correct horse".toCharArray())
        try {
            db.txnDao().deleteHard("tx1")
            archive.restore(file, "correct horse".toCharArray())
            assertEquals(1, db.txnDao().countByLedger("l1"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun wrongPassphraseFailsAndLeavesLiveDatabaseIntact() = runTest {
        seed()
        val file = tempSnapshot("wrong-pass")
        archive.create(file, "correct horse".toCharArray())
        try {
            assertFailsWith<Exception> { archive.restore(file, "wrong horse".toCharArray()) }
            assertEquals(1, db.txnDao().countByLedger("l1"), "恢复失败后主库必须保持原样")
            assertNotNull(db.txnDao().getById("tx1"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun tamperedDbJsonFailsChecksumAndLeavesLiveDatabaseIntact() = runTest {
        seed()
        val file = tempSnapshot("tampered")
        writeRawSnapshot(file, formatVersion = SnapshotArchive.FORMAT_VERSION, dbJson = """{"tables":{}}""")
        try {
            assertFailsWith<Exception> { archive.restore(file) }
            assertEquals(1, db.txnDao().countByLedger("l1"))
            assertNotNull(db.txnDao().getById("tx1"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun unknownFormatVersionFailsAndLeavesLiveDatabaseIntact() = runTest {
        seed()
        val file = tempSnapshot("bad-version")
        writeRawSnapshot(file, formatVersion = 999, dbJson = """{"tables":{}}""")
        try {
            val error = assertFailsWith<Exception> { archive.restore(file) }
            assertTrue(error.message.orEmpty().contains("format") || error is IllegalArgumentException)
            assertEquals(1, db.txnDao().countByLedger("l1"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun blobColumnsSurviveJsonRoundTrip() = runTest {
        seed()
        val raw = db.openHelper.writableDatabase
        raw.execSQL("ALTER TABLE attachment ADD COLUMN payload BLOB")
        val bytes = byteArrayOf(0, 1, 2, 3, -1, 127)
        raw.execSQL(
            "INSERT INTO attachment (id, txn_id, file_name, mime_type, size_bytes, relative_path, created_at, payload) " +
                "VALUES (?,?,?,?,?,?,?,?)",
            arrayOf<Any?>("att1", "tx1", "a.png", "image/png", bytes.size.toLong(), "x/a.png", 1L, bytes),
        )
        val file = File(context.cacheDir, "codec-${System.nanoTime()}.json")
        try {
            SnapshotJsonCodec.export(raw, SnapshotArchive.TABLES, file)
            SnapshotArchive.TABLES.asReversed().forEach { raw.execSQL("DELETE FROM `$it`") }
            SnapshotJsonCodec.insert(raw, SnapshotArchive.TABLES, file)
            raw.query("SELECT payload FROM attachment").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getBlob(0).contentEquals(bytes), "BLOB 往返必须无损")
            }
        } finally {
            file.delete()
        }
    }

    /**
     * 锁定「JSON 导出字段名 = `docs/DATA-MODEL.md` 的列名」这条契约：
     * 快照 `db.json` 与设置页 JSON 导出共用 [SnapshotJsonCodec]，改实体 `@ColumnInfo`
     * 而不改 DATA-MODEL 时这条测试会红，防止两侧静默漂移。
     */
    @Test
    fun exportedJsonUsesDataModelColumnNames() = runTest {
        seed()
        val file = File(context.cacheDir, "codec-names-${System.nanoTime()}.json")
        try {
            SnapshotJsonCodec.export(db.openHelper.writableDatabase, SnapshotArchive.TABLES, file)
            val txnRow = JSONObject(file.readText(Charsets.UTF_8))
                .getJSONObject("tables")
                .getJSONArray("txn")
                .getJSONObject(0)
            val expected = setOf(
                "id", "ledger_id", "type", "amount_cents", "account_id", "to_account_id",
                "category_id", "occurred_at", "title", "remark", "source", "draft_status",
                "exclude_from_stats", "recurring_rule_id", "installment_plan_id",
                "installment_period_no", "import_hash", "created_at", "updated_at", "deleted_at",
            )
            assertEquals(expected, txnRow.keys().asSequence().toSet())
        } finally {
            file.delete()
        }
    }

    /** 手工拼一个快照 zip：checksum 与 db.json 必然不匹配（用于校验篡改/版本两条失败路径）。 */
    private fun writeRawSnapshot(file: File, formatVersion: Int, dbJson: String) {
        ZipOutputStream(file.outputStream()).use { zip ->
            val manifest = """{"formatVersion":$formatVersion,"createdAt":0,"counts":{},"sha256OfDb":"${"0".repeat(64)}"}"""
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("db.json"))
            zip.write(dbJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
