package com.han1997.vipledger.core.data.backup

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.TransactionType
import java.io.File
import java.io.Writer
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
import org.json.JSONArray
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
    fun restoreRetainsBindingsButDisablesCapture() = runTest {
        seed()
        db.autoBookDao().saveConfig(com.han1997.vipledger.core.database.entity.AutoConfigEntity(
            ledgerId = "l1", enabled = true, ocrEnabled = true, enabledApps = "WECHAT", verifiedApps = "WECHAT"))
        db.autoBookDao().bind(com.han1997.vipledger.core.database.entity.AutoBindingEntity("l1", "WECHAT", "Wallet", "a1"))
        val file = tempSnapshot("capture")
        try {
            archive.create(file); archive.restore(file)
            assertEquals(false, db.autoBookDao().config()!!.enabled)
            assertEquals("", db.autoBookDao().config()!!.verifiedApps)
            assertEquals(1, db.autoBookDao().bindings("l1").size)
        } finally { file.delete() }
    }

    @Test
    fun legacySnapshotWithoutAutomaticTablesStillRestores() = runTest {
        seed()
        val jsonFile = File(context.cacheDir, "legacy-${System.nanoTime()}.json")
        val file = tempSnapshot("legacy")
        try {
            archive.exportDatabaseJson(jsonFile)
            val document = JSONObject(jsonFile.readText()).apply { remove("schemaVersion") }
            val tables = document.getJSONObject("tables")
            SnapshotArchive.TABLES.filter { it.startsWith("auto_") || it.startsWith("budget_") }.forEach(tables::remove)
            val rows = tables.getJSONArray("txn")
            for (i in 0 until rows.length()) rows.getJSONObject(i).remove("refund_of_txn_id")
            val bytes = document.toString().toByteArray()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val counts = JSONObject()
            tables.keys().forEach { counts.put(it, tables.getJSONArray(it).length()) }
            val manifest = JSONObject().put("formatVersion", 1).put("createdAt", 1).put("counts", counts).put("sha256OfDb", hash)
            ZipOutputStream(file.outputStream()).use {
                it.putNextEntry(ZipEntry("manifest.json")); it.write(manifest.toString().toByteArray()); it.closeEntry()
                it.putNextEntry(ZipEntry("db.json")); it.write(bytes); it.closeEntry()
            }
            db.txnDao().deleteHard("tx1")
            archive.restore(file)
            assertEquals(1000, db.txnDao().getById("tx1")!!.amountCents)
            assertEquals(null, db.txnDao().getById("tx1")!!.refundOfTxnId)
        } finally { file.delete(); jsonFile.delete() }
    }

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
                "installment_period_no", "import_hash", "refund_of_txn_id", "created_at", "updated_at", "deleted_at",
            )
            assertEquals(expected, txnRow.keys().asSequence().toSet())
        } finally {
            file.delete()
        }
    }

    /**
     * 关键的字节级回归网：流式导出的输出必须与**旧的整库构建方案**
     * （整库 JSONObject → `toString()`）逐字节一致。快照的 `sha256OfDb`
     * 就压在这份 `db.json` 上，任何一个分隔符或转义差别都会让
     * 所有既有用户快照校验失败。这里用真实 `SnapshotArchive.TABLES` 与
     * Room schema 覆盖全部 12 张表（含空表 `[]` 与中文文本）。
     */
    @Test
    fun streamedExportIsByteIdenticalToWholeObjectBuild() = runTest {
        seed()
        val file = File(context.cacheDir, "codec-identity-${System.nanoTime()}.json")
        try {
            SnapshotJsonCodec.export(db.openHelper.writableDatabase, SnapshotArchive.TABLES, file)
            val actual = file.readText(Charsets.UTF_8)
            val expected = referenceWholeObjectExport(db.openHelper.writableDatabase, SnapshotArchive.TABLES)
            assertEquals(expected, actual, "流式导出的字节必须与整库构建方案完全一致")
        } finally {
            file.delete()
        }
    }

    /**
     * 用一个受控的临时表锁定「导出即 `db.json` 的字面形状」：
     * 整数写成数字、浮点写成数字、BLOB 写成 `Base64(NO_WRAP)`、字符串按
     * `org.json` 规则转义，表名/列名直接用 SQLite 实际名字。
     */
    @Test
    fun streamedExportMatchesPinnedLiteralBytes() = runTest {
        val raw = db.openHelper.writableDatabase
        raw.execSQL("CREATE TABLE stream_fixture (a INTEGER, b TEXT, c REAL, d BLOB)")
        raw.execSQL(
            "INSERT INTO stream_fixture (a, b, c, d) VALUES (?, ?, ?, ?)",
            arrayOf<Any?>(1L, "quote\"back\\slash", 2.5, byteArrayOf(0x00, 0xFF.toByte())),
        )
        val file = File(context.cacheDir, "codec-literal-${System.nanoTime()}.json")
        try {
            val counts = SnapshotJsonCodec.export(raw, listOf("stream_fixture"), file)
            assertEquals(1, counts["stream_fixture"])
            assertEquals(
                """{"schemaVersion":${LedgerDatabase.SCHEMA_VERSION},"tables":{"stream_fixture":[{"a":1,"b":"quote\"back\\slash","c":2.5,"d":"AP8="}]}}""",
                file.readText(Charsets.UTF_8),
            )
        } finally {
            file.delete()
        }
    }

    /**
     * 锁定流式性质：导出必须逐行写出，内存里只有一行。
     *
     * 旧实现会先把整库塞进一个 `JSONObject`，最后只有**一次**超大 `write`；
     * 因此「写入次数应远多于行数、且单次写入远小于整份文档」这条断言在
     * 有人退回整库构建时必然变红。这比测堆内存稳定，不依赖 GC 时机。
     */
    @Test
    fun exportStreamsRowByRowInsteadOfBuildingWholeDocument() = runTest {
        val raw = db.openHelper.writableDatabase
        raw.execSQL("CREATE TABLE stream_rows (id INTEGER PRIMARY KEY, payload TEXT)")
        val rowCount = 2_000
        val payload = "x".repeat(256)
        raw.beginTransaction()
        try {
            repeat(rowCount) { raw.execSQL("INSERT INTO stream_rows (payload) VALUES (?)", arrayOf(payload)) }
            raw.setTransactionSuccessful()
        } finally {
            raw.endTransaction()
        }

        val recorder = RecordingWriter()
        val counts = SnapshotJsonCodec.export(raw, listOf("stream_rows"), recorder)

        assertEquals(rowCount, counts["stream_rows"])
        val output = recorder.text.toString()
        assertTrue(output.startsWith("{\"schemaVersion\":${LedgerDatabase.SCHEMA_VERSION},\"tables\":{\"stream_rows\":["), "结构前缀必须稳定")
        assertTrue(output.endsWith("]}}"), "结构后缀必须稳定")
        assertTrue(
            recorder.chunks.size > rowCount,
            "应逐行写出（多次小写入），而不是整库一次性写出；实际 write 次数=${recorder.chunks.size}",
        )
        assertTrue(
            recorder.chunks.max() < output.length / 4,
            "单次写入不应接近整份文档大小；最大 chunk=${recorder.chunks.max()}, 文档=${output.length}",
        )
    }

    /** 旧的整体构建实现的原样复刻，仅作字节级对照，不参与生产路径。 */
    private fun referenceWholeObjectExport(database: SupportSQLiteDatabase, tables: List<String>): String {
        val json = JSONObject().put("schemaVersion", LedgerDatabase.SCHEMA_VERSION)
        val tableJson = JSONObject()
        tables.forEach { table ->
            val rows = JSONArray()
            database.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (index in 0 until cursor.columnCount) {
                        val value: Any = when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                            Cursor.FIELD_TYPE_BLOB ->
                                android.util.Base64.encodeToString(cursor.getBlob(index), android.util.Base64.NO_WRAP)
                            else -> cursor.getString(index)
                        }
                        row.put(cursor.getColumnName(index), value)
                    }
                    rows.put(row)
                }
            }
            tableJson.put(table, rows)
        }
        json.put("tables", tableJson)
        return json.toString()
    }

    /** 记录每次 `write` 的块大小并拼接完整输出，用于观测导出是否流式。 */
    private class RecordingWriter : Writer() {
        val chunks = mutableListOf<Int>()
        val text = StringBuilder()

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            chunks += len
            text.append(cbuf, off, len)
        }

        override fun flush() = Unit

        override fun close() = Unit
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
