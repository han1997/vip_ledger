package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.han1997.vipledger.core.database.LedgerDatabase
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class SnapshotManifest(
    val formatVersion: Int,
    val createdAt: Long,
    val counts: Map<String, Int>,
    val sha256OfDb: String,
)

/**
 * 数据库快照的创建/恢复。
 *
 * 安全边界（`.trellis/spec/android/import-export.md`、`error-handling.md`）：
 * 恢复时先在**临时库**里写入并校验行数、外键与校验和，全部通过后才替换主库；
 * 任何一步失败都直接抛异常，主库保持原样。
 *
 * 恢复前会先给当前主库留一份本地安全副本（[SAFETY_COPY_LIMIT] 份滚动保留），
 * 恢复本身只留这一份，不再叠加第二份。
 */
class SnapshotArchive(private val context: Context, private val database: LedgerDatabase) {

    fun create(destination: File, passphrase: CharArray? = null): SnapshotManifest {
        val dbJson = File(context.cacheDir, "snapshot-db-${System.nanoTime()}.json")
        try {
            val counts = SnapshotJsonCodec.export(database.openHelper.readableDatabase, TABLES, dbJson)
            val checksum = sha256(dbJson)
            val manifest = SnapshotManifest(FORMAT_VERSION, System.currentTimeMillis(), counts, checksum)
            FileOutputStream(destination).buffered().use { raw ->
                val output = if (passphrase == null) raw else encryptedOutput(raw, passphrase)
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(
                        JSONObject().put("formatVersion", manifest.formatVersion)
                            .put("createdAt", manifest.createdAt)
                            .put("counts", JSONObject(manifest.counts))
                            .put("sha256OfDb", manifest.sha256OfDb).toString().toByteArray(Charsets.UTF_8),
                    )
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("db.json"))
                    FileInputStream(dbJson).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            return manifest
        } finally {
            dbJson.delete()
            passphrase?.fill('\u0000')
        }
    }

    fun restore(source: File, passphrase: CharArray? = null): SnapshotManifest {
        val dbJson = File(context.cacheDir, "restore-db-${System.nanoTime()}.json")
        val manifestFile = File(context.cacheDir, "restore-manifest-${System.nanoTime()}.json")
        try {
            FileInputStream(source).buffered().use { input ->
                val decoded = if (hasEncryptedHeader(source)) {
                    decryptedInput(input, passphrase ?: throw IllegalArgumentException("Passphrase required"))
                } else {
                    input
                }
                ZipInputStream(BufferedInputStream(decoded)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "db.json" -> FileOutputStream(dbJson).use { zip.copyTo(it) }
                            "manifest.json" -> FileOutputStream(manifestFile).use { zip.copyTo(it) }
                            else -> if (entry.name.startsWith("/") || entry.name.contains("..")) {
                                throw IllegalArgumentException("Invalid archive entry")
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val manifestJson = JSONObject(manifestFile.readText(Charsets.UTF_8))
            val version = manifestJson.getInt("formatVersion")
            require(version in 1..FORMAT_VERSION) { "Unsupported backup format" }
            val expected = manifestJson.getString("sha256OfDb")
            require(expected.equals(sha256(dbJson), ignoreCase = true)) { "Backup checksum mismatch" }
            val countsJson = manifestJson.getJSONObject("counts")
            val counts = TABLES.associateWith { table ->
                if (version < tableVersion(table) && !countsJson.has(table)) 0 else countsJson.getInt(table)
            }
            val manifest = SnapshotManifest(version, manifestJson.getLong("createdAt"), counts, expected)
            validateWithTemporaryDatabase(dbJson, counts)
            createSafetyCopy()
            applyDatabaseJson(dbJson)
            return manifest
        } finally {
            dbJson.delete()
            manifestFile.delete()
            passphrase?.fill('\u0000')
        }
    }

    /** 设置页「导出 JSON」用的可读迁移文件；与快照里的 `db.json` 是同一份编解码。 */
    fun exportDatabaseJson(file: File) {
        SnapshotJsonCodec.export(database.openHelper.readableDatabase, TABLES, file)
    }

    private fun validateWithTemporaryDatabase(file: File, expectedCounts: Map<String, Int>) {
        val name = "restore-validation-${System.nanoTime()}.db"
        val temp = Room.databaseBuilder(context, LedgerDatabase::class.java, name)
            .addMigrations(*com.han1997.vipledger.core.database.ALL_MIGRATIONS).build()
        try {
            temp.openHelper.writableDatabase.beginTransaction()
            try {
                SnapshotJsonCodec.insert(temp.openHelper.writableDatabase, TABLES, file)
                val result = temp.openHelper.writableDatabase.query("PRAGMA foreign_key_check")
                check(!result.use { it.moveToNext() }) { "Backup contains invalid references" }
                validateWealth(temp.openHelper.writableDatabase)
                TABLES.forEach { table ->
                    temp.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                        check(cursor.moveToFirst() && cursor.getInt(0) == expectedCounts.getValue(table))
                    }
                }
                temp.openHelper.writableDatabase.setTransactionSuccessful()
            } finally {
                temp.openHelper.writableDatabase.endTransaction()
            }
        } finally {
            temp.close()
            context.getDatabasePath(name).delete()
            File(context.getDatabasePath(name).path + "-wal").delete()
            File(context.getDatabasePath(name).path + "-shm").delete()
        }
    }

    private fun validateWealth(db: SupportSQLiteDatabase) {
        val checks = listOf(
            "SELECT 1 FROM savings_goal g LEFT JOIN account a ON a.id=g.account_id WHERE a.id IS NULL OR a.ledger_id!=g.ledger_id OR g.target_cents<=0 LIMIT 1",
            "SELECT 1 FROM savings_link s JOIN savings_goal g ON g.id=s.goal_id JOIN txn t ON t.id=s.txn_id WHERE t.ledger_id!=g.ledger_id OR t.type!='TRANSFER' OR t.amount_cents<=0 OR t.draft_status!='CONFIRMED' OR t.account_id=COALESCE(t.to_account_id,'') OR (t.account_id!=g.account_id AND COALESCE(t.to_account_id,'')!=g.account_id) LIMIT 1",
            "SELECT g.id FROM savings_goal g JOIN savings_link s ON s.goal_id=g.id JOIN txn t ON t.id=s.txn_id WHERE t.deleted_at IS NULL GROUP BY g.id HAVING SUM(CASE WHEN t.to_account_id=g.account_id THEN t.amount_cents ELSE -t.amount_cents END)<0 LIMIT 1",
            "SELECT 1 FROM asset_baseline b LEFT JOIN account a ON a.id=b.account_id WHERE a.id IS NULL OR a.ledger_id!=b.ledger_id LIMIT 1",
        )
        checks.forEach { query -> check(!db.query(query).use { it.moveToFirst() }) { "Invalid savings or asset relationships" } }
        db.query("SELECT deadline FROM savings_goal WHERE deadline IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) kotlinx.datetime.LocalDate.parse(cursor.getString(0))
        }
    }

    private fun applyDatabaseJson(file: File) {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            TABLES.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
            SnapshotJsonCodec.insert(db, TABLES, file)
            db.execSQL("UPDATE auto_config SET enabled = 0, verifiedApps = ''")
            check(!db.query("PRAGMA foreign_key_check").use { it.moveToNext() })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // 原始 SQL 写入绕过 Room 的失效追踪，必须手动刷新，否则恢复后界面仍读旧数据。
        database.invalidationTracker.refreshAsync()
    }

    /** 恢复前给当前主库留一份本地安全副本，并滚动清理旧副本。 */
    private fun createSafetyCopy() {
        val directory = File(context.filesDir, SAFETY_COPY_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        create(File(directory, "$SAFETY_COPY_PREFIX$stamp.zip"))
        directory.listFiles()
            ?.filter { it.name.startsWith(SAFETY_COPY_PREFIX) && it.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?.drop(SAFETY_COPY_LIMIT)
            ?.forEach { it.delete() }
    }

    private fun encryptedOutput(output: OutputStream, passphrase: CharArray): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        output.write(MAGIC)
        output.write(salt)
        output.write(iv)
        val keySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        keySpec.clearPassword()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        key.fill(0)
        return CipherOutputStream(output, cipher)
    }

    private fun decryptedInput(input: InputStream, passphrase: CharArray): InputStream {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        val header = ByteArray(MAGIC.size + SALT_BYTES + IV_BYTES)
        require(buffered.read(header) == header.size && header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC))
        val salt = header.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = header.copyOfRange(MAGIC.size + SALT_BYTES, header.size)
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        key.fill(0)
        return CipherInputStream(buffered, cipher)
    }

    private fun hasEncryptedHeader(file: File): Boolean = FileInputStream(file).use { input ->
        val actual = ByteArray(MAGIC.size)
        input.read(actual) == actual.size && actual.contentEquals(MAGIC)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val FORMAT_VERSION = 4
        fun tableVersion(table: String): Int = when {
            table in setOf("savings_goal", "savings_link", "home_card", "asset_baseline") -> 4
            table.startsWith("budget_") -> 3
            table.startsWith("auto_") -> 2
            else -> 1
        }
        const val PBKDF2_ITERATIONS = 150_000
        const val SAFETY_COPY_DIR = "backups"
        const val SAFETY_COPY_PREFIX = "pre-restore-"
        const val SAFETY_COPY_LIMIT = 5
        private val MAGIC = "VLENC001".toByteArray(Charsets.US_ASCII)
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        val TABLES = listOf(
            "ledger", "account", "category", "tag", "txn", "txn_tag", "recurring_rule",
            "installment_plan", "installment_period", "merchant_rule", "attachment", "ml_training_sample",
            "auto_config", "auto_binding", "auto_candidate", "auto_evidence",
            "budget_limit", "budget_alert",
            "savings_goal", "savings_link", "home_card", "asset_baseline",
        )
    }
}
