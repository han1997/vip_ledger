package com.han1997.vipledger.feature.sync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.data.backup.BackupIoManager
import com.han1997.vipledger.core.data.backup.BackupLogStore
import com.han1997.vipledger.core.data.backup.BackupScheduler
import com.han1997.vipledger.core.data.importexport.CsvField
import com.han1997.vipledger.core.data.importexport.CsvMapping
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.sync.ui.common.SyncExport
import com.han1997.vipledger.feature.sync.ui.common.csvSuggestedFileName
import com.han1997.vipledger.feature.sync.ui.common.jsonSuggestedFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 备份 / 恢复 / 导入导出页的状态持有者。
 *
 * 只依赖数据层的 [BackupIoManager] 与领域用例；不碰 `Uri`（SAF 写盘由界面层完成），
 * 一次性提示用 [SyncNotice] 枚举，中文文案在 Screen 里查 `strings.xml`。
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    application: Application,
    private val manager: BackupIoManager,
    private val timeProvider: TimeProvider,
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
) : AndroidViewModel(application) {

    private val mutableState = MutableStateFlow(BackupUiState(settings = manager.settings()))
    val uiState: StateFlow<BackupUiState> = mutableState.asStateFlow()

    private val logStore = BackupLogStore(application)
    private var importText: String? = null
    private var currentPreset: ImportPreset = ImportPreset.WECHAT

    init {
        viewModelScope.launch {
            observeCurrentLedger().collect { ledger ->
                val accounts = ledger?.let { manager.accountOptions(it.id) }.orEmpty()
                mutableState.update {
                    it.copy(
                        ledgerId = ledger?.id,
                        accounts = accounts,
                        accountId = it.accountId.ifBlank { accounts.firstOrNull()?.first.orEmpty() },
                    )
                }
            }
        }
        mutableState.update { it.copy(backupLog = logStore.read()) }
    }

    fun onAction(action: BackupAction) {
        when (action) {
            is BackupAction.Save -> execute {
                manager.saveSettings(action.settings)
                BackupScheduler.configure(getApplication(), action.settings.scheduled, action.settings.wifiOnly)
                mutableState.update { it.copy(settings = action.settings) }
                NoticeResult(SyncNotice.SAVED)
            }

            BackupAction.TestConnection -> execute {
                NoticeResult(if (manager.testWebDav()) SyncNotice.CONNECTION_OK else SyncNotice.CONNECTION_FAILED)
            }

            BackupAction.BackupNow -> execute {
                val name = manager.backupRemote()
                mutableState.update { it.copy(backupLog = logStore.read()) }
                NoticeResult(SyncNotice.BACKUP_DONE, name)
            }

            BackupAction.RefreshSnapshots -> execute {
                val snapshots = manager.listRemote()
                mutableState.update { it.copy(snapshots = snapshots) }
                NoticeResult(SyncNotice.SNAPSHOTS_REFRESHED)
            }

            is BackupAction.DeleteSnapshot -> execute {
                manager.deleteRemote(action.name)
                mutableState.update { it.copy(snapshots = it.snapshots.filterNot { s -> s.name == action.name }) }
                NoticeResult(SyncNotice.SNAPSHOT_DELETED)
            }

            is BackupAction.Restore -> execute {
                manager.restoreRemote(action.name)
                NoticeResult(SyncNotice.RESTORED)
            }

            is BackupAction.ReadImport -> {
                importText = action.content
                currentPreset = action.preset
                mutableState.update { it.copy(preset = action.preset, importReport = null) }
                refreshPreview(mapping = null)
            }

            is BackupAction.SelectPreset -> {
                currentPreset = action.preset
                mutableState.update { it.copy(preset = action.preset) }
                refreshPreview(mapping = null)
            }

            is BackupAction.SelectAccount -> mutableState.update { it.copy(accountId = action.id) }

            is BackupAction.ChangeColumn -> {
                val base = mutableState.value.selectedMapping?.columns
                    ?: mutableState.value.preview?.mapping?.columns
                    ?: emptyMap()
                val columns = base.toMutableMap()
                if (action.column == null) columns.remove(action.field) else columns[action.field] = action.column
                refreshPreview(CsvMapping(columns))
            }

            BackupAction.Import -> importCsv()

            BackupAction.ExportCsv -> execute {
                val ledgerId = mutableState.value.ledgerId ?: return@execute NoticeResult(SyncNotice.OPERATION_FAILED)
                val csv = manager.exportCsv(ledgerId)
                mutableState.update {
                    it.copy(pendingExport = SyncExport(csvFileName(), csv, CSV_MIME))
                }
                NoticeResult(null)
            }

            BackupAction.ExportJson -> execute {
                val file = File(getApplication<Application>().cacheDir, "vipledger-export.json")
                try {
                    manager.exportJson(file)
                    val content = file.readText(Charsets.UTF_8)
                    mutableState.update {
                        it.copy(pendingExport = SyncExport(jsonFileName(), content, JSON_MIME))
                    }
                } finally {
                    file.delete()
                }
                NoticeResult(null)
            }

            is BackupAction.ExportFinished -> mutableState.update {
                it.copy(notice = if (action.success) SyncNotice.EXPORTED else SyncNotice.EXPORT_FAILED)
            }

            BackupAction.ConsumeExport -> mutableState.update { it.copy(pendingExport = null) }

            BackupAction.ConsumeNotice -> mutableState.update { it.copy(notice = null, noticeDetail = null) }
        }
    }

    private fun refreshPreview(mapping: CsvMapping?) {
        val text = importText ?: return
        runCatching { manager.previewCsv(text, presetArgument(currentPreset), mapping) }
            .onSuccess { preview ->
                mutableState.update { it.copy(preview = preview, selectedMapping = preview.mapping) }
            }
            .onFailure { mutableState.update { it.copy(notice = SyncNotice.IMPORT_PREVIEW_FAILED) } }
    }

    private fun importCsv() {
        val state = mutableState.value
        val ledgerId = state.ledgerId ?: return
        val mapping = state.selectedMapping ?: return
        val text = importText ?: return
        if (state.accountId.isBlank() || mapping.missingRequired().isNotEmpty()) return
        execute {
            val report = manager.importCsv(ledgerId, state.accountId, text, mapping, presetArgument(currentPreset))
            mutableState.update { it.copy(importReport = report) }
            NoticeResult(SyncNotice.IMPORT_DONE)
        }
    }

    private fun csvFileName(): String = csvSuggestedFileName(PREFIX_EXPORT, currentDate())

    private fun jsonFileName(): String = jsonSuggestedFileName(PREFIX_EXPORT, currentDate())

    private fun currentDate() = timeProvider.nowMillis().toLocalDate(timeProvider.zone)

    private fun presetArgument(preset: ImportPreset): String? = when (preset) {
        ImportPreset.WECHAT -> "wechat"
        ImportPreset.ALIPAY -> "alipay"
        ImportPreset.GENERIC -> null
    }

    private fun execute(block: suspend () -> NoticeResult) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
                .getOrElse { NoticeResult(SyncNotice.OPERATION_FAILED) }
            mutableState.update { it.copy(busy = false, notice = result.notice, noticeDetail = result.detail) }
        }
    }

    private data class NoticeResult(val notice: SyncNotice?, val detail: String? = null)

    private companion object {
        const val PREFIX_EXPORT = "export"
        const val CSV_MIME = "text/csv"
        const val JSON_MIME = "application/json"
    }
}
