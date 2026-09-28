package com.han1997.vipledger.feature.sync.ui

import com.han1997.vipledger.core.data.backup.BackupLogEntry
import com.han1997.vipledger.core.data.backup.BackupSettings
import com.han1997.vipledger.core.data.backup.RemoteSnapshot
import com.han1997.vipledger.core.data.importexport.CsvField
import com.han1997.vipledger.core.data.importexport.CsvMapping
import com.han1997.vipledger.core.data.importexport.CsvPreview
import com.han1997.vipledger.core.data.importexport.ImportReport
import com.han1997.vipledger.feature.sync.ui.common.SyncExport

/** 导入预设：支付宝 / 微信走内置映射，通用需要用户手动选列。 */
enum class ImportPreset { WECHAT, ALIPAY, GENERIC }

/** 一次性提示，界面层再查 `strings.xml`（ui.md：状态里不放中文）。 */
enum class SyncNotice {
    SAVED,
    CONNECTION_OK,
    CONNECTION_FAILED,
    BACKUP_DONE,
    SNAPSHOTS_REFRESHED,
    RESTORED,
    SNAPSHOT_DELETED,
    IMPORT_DONE,
    IMPORT_PREVIEW_FAILED,
    EXPORTED,
    EXPORT_FAILED,
    OPERATION_FAILED,
}

data class BackupUiState(
    val settings: BackupSettings = BackupSettings(),
    val ledgerId: String? = null,
    val accounts: List<Pair<String, String>> = emptyList(),
    val accountId: String = "",
    val snapshots: List<RemoteSnapshot> = emptyList(),
    val backupLog: List<BackupLogEntry> = emptyList(),
    val preset: ImportPreset = ImportPreset.WECHAT,
    val preview: CsvPreview? = null,
    val importReport: ImportReport? = null,
    val selectedMapping: CsvMapping? = null,
    val pendingExport: SyncExport? = null,
    val busy: Boolean = false,
    val notice: SyncNotice? = null,
    val noticeDetail: String? = null,
) {
    /** 必填列缺失 —— 界面据此禁用「开始导入」。 */
    val missingRequired: Set<CsvField> get() = selectedMapping?.missingRequired().orEmpty()
}

sealed interface BackupAction {
    data class Save(val settings: BackupSettings) : BackupAction
    data object TestConnection : BackupAction
    data object BackupNow : BackupAction
    data object RefreshSnapshots : BackupAction
    data class DeleteSnapshot(val name: String) : BackupAction
    data class Restore(val name: String) : BackupAction
    data class ReadImport(val content: String, val preset: ImportPreset) : BackupAction
    data class SelectPreset(val preset: ImportPreset) : BackupAction
    data class SelectAccount(val id: String) : BackupAction
    data class ChangeColumn(val field: CsvField, val column: Int?) : BackupAction
    data object Import : BackupAction
    data object ExportCsv : BackupAction
    data object ExportJson : BackupAction
    data class ExportFinished(val success: Boolean) : BackupAction
    data object ConsumeExport : BackupAction
    data object ConsumeNotice : BackupAction
}
