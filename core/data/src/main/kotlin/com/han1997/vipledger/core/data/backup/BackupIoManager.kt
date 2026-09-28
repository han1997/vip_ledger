package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.dao.AccountDao
import com.han1997.vipledger.core.database.dao.CategoryDao
import com.han1997.vipledger.core.database.dao.TagDao
import com.han1997.vipledger.core.database.dao.TxnDao
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.data.importexport.CsvLedgerImporter
import com.han1997.vipledger.core.data.importexport.CsvMapping
import com.han1997.vipledger.core.data.importexport.CsvPreview
import com.han1997.vipledger.core.data.importexport.ImportReport
import com.han1997.vipledger.core.data.mapper.toDomain
import com.han1997.vipledger.core.domain.support.AccountBalanceSyncer
import com.han1997.vipledger.core.domain.usecase.backup.BuildBackupCsvUseCase
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TxnSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class BackupSettings(
    val endpoint: String = "",
    val path: String = "vipledger",
    val username: String = "",
    val password: String = "",
    val passphrase: String = "",
    val encryptionEnabled: Boolean = false,
    val scheduled: Boolean = false,
    val wifiOnly: Boolean = true,
    val retention: Int = 10,
)

/**
 * 备份/导入导出的数据层入口：WebDAV 快照与用户侧 CSV/JSON 导出。
 *
 * 该类只做编排，格式细节放在 `core:domain`（CSV）与 [SnapshotJsonCodec]（JSON 表数据）；
 * 凭据/口令通过 [EncryptedSettingsStore] 加密保存，任何路径都不写日志。
 */
@Singleton
class BackupIoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LedgerDatabase,
    private val txnDao: TxnDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val balanceSyncer: AccountBalanceSyncer,
    private val buildBackupCsv: BuildBackupCsvUseCase,
) {
    private val secrets = EncryptedSettingsStore(context)

    fun settings(): BackupSettings = secrets.read()

    fun saveSettings(settings: BackupSettings) {
        require(settings.retention in 1..100)
        secrets.write(settings)
    }

    fun testWebDav(): Boolean = client(settings()).check()

    fun listRemote(): List<RemoteSnapshot> = client(settings()).list()

    fun deleteRemote(name: String) = client(settings()).delete(name)

    fun backupRemote(): String {
        val config = settings()
        require(config.endpoint.isNotBlank())
        val passphrase = config.passphrase.takeIf { config.encryptionEnabled }?.toCharArray()
        val name = "vipledger-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.zip"
        val file = File(context.cacheDir, name)
        val webdav = client(config)
        return try {
            SnapshotArchive(context, database).create(file, passphrase)
            webdav.upload(file, name)
            webdav.retain(config.retention)
            name
        } finally {
            file.delete()
        }
    }

    /**
     * 恢复远端快照。本地安全副本由 [SnapshotArchive.restore] 统一负责（只留一份），
     * 这里不再重复创建，避免每次恢复产生两份副本。
     */
    fun restoreRemote(name: String): SnapshotManifest {
        val config = settings()
        val file = File(context.cacheDir, "restore-${System.nanoTime()}.zip")
        val webdav = client(config)
        return try {
            webdav.download(name, file)
            val passphrase = config.passphrase.takeIf { it.isNotBlank() }?.toCharArray()
            SnapshotArchive(context, database).restore(file, passphrase)
        } finally {
            file.delete()
        }
    }

    /** M5 对外格式 CSV（`.trellis/spec/android/import-export.md` 第一节）。 */
    suspend fun exportCsv(ledgerId: String): String {
        val txns = txnDao.listFiltered(
            ledgerId = ledgerId,
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
        ).map { it.toDomain() }
        val categories = categoryDao.listByLedger(ledgerId, includeArchived = true).map { it.toDomain() }
        val accountNames = accountDao.listByLedger(ledgerId, includeArchived = true).associate { it.id to it.name }
        val tagNames = tagDao.listByLedger(ledgerId).associate { it.id to it.name }
        return buildBackupCsv(txns, categories, accountNames, tagNames)
    }

    /** 用户侧 JSON 导出：与快照 `db.json` 共用同一份表数据编解码（见 [SnapshotJsonCodec]）。 */
    fun exportJson(file: File) {
        SnapshotArchive(context, database).exportDatabaseJson(file)
    }

    fun previewCsv(text: String, preset: String? = null, mapping: CsvMapping? = null): CsvPreview =
        CsvLedgerImporter.preview(text, preset, mapping)

    suspend fun accountOptions(ledgerId: String): List<Pair<String, String>> =
        accountDao.listByLedger(ledgerId, false).map { it.id to it.name }

    suspend fun importCsv(
        ledgerId: String,
        accountId: String,
        text: String,
        mapping: CsvMapping,
        preset: String? = null,
    ): ImportReport {
        require(accountDao.getById(accountId)?.ledgerId == ledgerId)
        val parsed = CsvLedgerImporter.parse(text, mapping, preset)
        var duplicateCount = 0
        var insertedCount = 0
        val changedAccounts = mutableSetOf<String>()
        return try {
            database.withTransaction {
                parsed.accepted.forEach { row ->
                    val hash = CsvLedgerImporter.fingerprint(row.occurredAt, row.amountCents, accountId, row.title)
                    // import_hash 是全库唯一索引：命中即重复，整批原子提交、失败回滚。
                    if (txnDao.existsImportHash(hash)) {
                        duplicateCount++
                    } else {
                        val now = System.currentTimeMillis()
                        txnDao.upsert(
                            TxnEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                ledgerId = ledgerId,
                                type = row.type,
                                amountCents = row.amountCents,
                                accountId = accountId,
                                occurredAt = row.occurredAt,
                                title = row.title,
                                remark = row.remark,
                                source = TxnSource.IMPORT,
                                draftStatus = DraftStatus.CONFIRMED,
                                importHash = hash,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        insertedCount++
                        changedAccounts += accountId
                    }
                }
                balanceSyncer.syncBalances(changedAccounts, System.currentTimeMillis())
            }
            ImportReport(insertedCount, duplicateCount, parsed.failedLines)
        } catch (_: Exception) {
            ImportReport(0, 0, (parsed.failedLines + parsed.accepted.map { it.sourceLine }).distinct().sorted())
        }
    }

    private fun client(config: BackupSettings) = WebDavClient(config.endpoint, config.path, config.username, config.password)
}
