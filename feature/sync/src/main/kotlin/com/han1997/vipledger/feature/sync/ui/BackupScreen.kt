package com.han1997.vipledger.feature.sync.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.data.backup.BackupLogEntry
import com.han1997.vipledger.core.data.backup.BackupSettings
import com.han1997.vipledger.core.data.backup.RemoteSnapshot
import com.han1997.vipledger.core.data.importexport.CsvField
import com.han1997.vipledger.core.data.importexport.CsvMapping
import com.han1997.vipledger.core.data.importexport.CsvPreview
import com.han1997.vipledger.core.data.importexport.ImportReport
import com.han1997.vipledger.core.designsystem.component.VipLedgerConfirmDialog
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.feature.sync.R
import com.han1997.vipledger.feature.sync.ui.common.SyncExport
import com.han1997.vipledger.feature.sync.ui.common.readTextFromUri
import com.han1997.vipledger.feature.sync.ui.common.writeTextToUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备份 / 恢复 / 导入导出设置页。 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackupContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun BackupContent(
    state: BackupUiState,
    onAction: (BackupAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var draft by remember { mutableStateOf(state.settings) }
    var pendingExport by remember { mutableStateOf<SyncExport?>(null) }
    var confirmRestore by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    // 用户可能先切预设再选文件；回调里必须拿到「最新」预设，不能用首帧闭包捕获的值。
    val latestPreset by rememberUpdatedState(state.preset)

    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        val export = pendingExport
        pendingExport = null
        onAction(BackupAction.ExportFinished(uri != null && export != null && writeTextToUri(context, uri, export.content)))
    }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        val export = pendingExport
        pendingExport = null
        onAction(BackupAction.ExportFinished(uri != null && export != null && writeTextToUri(context, uri, export.content)))
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val content = uri?.let { readTextFromUri(context, it) }
        if (content != null) onAction(BackupAction.ReadImport(content, latestPreset))
    }

    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
        pendingExport = export
        if (export.mime == JSON_MIME) createJson.launch(export.fileName) else createCsv.launch(export.fileName)
        onAction(BackupAction.ConsumeExport)
    }

    val noticeMessage = state.notice?.let { syncNoticeMessage(it, state.noticeDetail, context) }
    LaunchedEffect(state.notice, state.noticeDetail) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(BackupAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { VipLedgerTopBar(title = stringResource(R.string.backup_title), onBack = onBack) },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (state.busy) {
                item(key = "busy") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.backup_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "webdav") {
                WebDavSection(
                    draft = draft,
                    onChange = { draft = it },
                    onAction = onAction,
                )
            }

            item(key = "snapshot") {
                SnapshotSection(
                    state = state,
                    onAction = onAction,
                    onRequestRestore = { confirmRestore = it },
                    onRequestDelete = { confirmDelete = it },
                )
            }

            item(key = "log") {
                BackupLogSection(entries = state.backupLog)
            }

            item(key = "export") {
                ExportSection(state = state, onAction = onAction)
            }

            val preview = state.preview
            if (preview != null) {
                item(key = "import-wizard") {
                    ImportSection(state = state, preview = preview, onAction = onAction, onPickFile = { openDocument.launch(FILE_MIME) })
                }
            } else {
                item(key = "import-empty") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SectionTitle(stringResource(R.string.backup_section_import))
                        PresetChips(state.preset) { onAction(BackupAction.SelectPreset(it)) }
                        Button(onClick = { openDocument.launch(FILE_MIME) }) {
                            Text(stringResource(R.string.backup_pick_file))
                        }
                    }
                }
            }
        }
    }

    confirmRestore?.let { name ->
        VipLedgerConfirmDialog(
            title = stringResource(R.string.backup_restore_title),
            message = stringResource(R.string.backup_restore_message),
            confirmLabel = stringResource(R.string.backup_action_confirm),
            dismissLabel = stringResource(R.string.backup_action_cancel),
            onConfirm = {
                confirmRestore = null
                onAction(BackupAction.Restore(name))
            },
            onDismiss = { confirmRestore = null },
            danger = true,
        )
    }
    confirmDelete?.let { name ->
        VipLedgerConfirmDialog(
            title = stringResource(R.string.backup_delete_title),
            message = stringResource(R.string.backup_delete_message),
            confirmLabel = stringResource(R.string.backup_delete),
            dismissLabel = stringResource(R.string.backup_action_cancel),
            onConfirm = {
                confirmDelete = null
                onAction(BackupAction.DeleteSnapshot(name))
            },
            onDismiss = { confirmDelete = null },
            danger = true,
        )
    }
}

@Composable
private fun WebDavSection(
    draft: BackupSettings,
    onChange: (BackupSettings) -> Unit,
    onAction: (BackupAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(stringResource(R.string.backup_section_webdav))
        LabeledTextField(
            label = stringResource(R.string.backup_endpoint),
            value = draft.endpoint,
            onValueChange = { onChange(draft.copy(endpoint = it)) },
            placeholder = stringResource(R.string.backup_endpoint_hint),
        )
        LabeledTextField(
            label = stringResource(R.string.backup_path),
            value = draft.path,
            onValueChange = { onChange(draft.copy(path = it)) },
        )
        LabeledTextField(
            label = stringResource(R.string.backup_username),
            value = draft.username,
            onValueChange = { onChange(draft.copy(username = it)) },
        )
        LabeledTextField(
            label = stringResource(R.string.backup_password),
            value = draft.password,
            onValueChange = { onChange(draft.copy(password = it)) },
            keyboardType = KeyboardType.Password,
        )
        LabeledTextField(
            label = stringResource(R.string.backup_passphrase),
            value = draft.passphrase,
            onValueChange = { onChange(draft.copy(passphrase = it)) },
            keyboardType = KeyboardType.Password,
        )
        ToggleRow(
            label = stringResource(R.string.backup_encryption),
            checked = draft.encryptionEnabled,
            onCheckedChange = { onChange(draft.copy(encryptionEnabled = it)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onAction(BackupAction.Save(draft)) }) {
                Text(stringResource(R.string.backup_save))
            }
            OutlinedButton(onClick = { onAction(BackupAction.TestConnection) }) {
                Text(stringResource(R.string.backup_test))
            }
        }
    }
}

@Composable
private fun SnapshotSection(
    state: BackupUiState,
    onAction: (BackupAction) -> Unit,
    onRequestRestore: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(stringResource(R.string.backup_section_snapshot))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onAction(BackupAction.BackupNow) }) {
                Text(stringResource(R.string.backup_now))
            }
            OutlinedButton(onClick = { onAction(BackupAction.RefreshSnapshots) }) {
                Text(stringResource(R.string.backup_refresh))
            }
        }
        LabeledTextField(
            label = stringResource(R.string.backup_retention),
            value = state.settings.retention.toString(),
            onValueChange = { value ->
                // 只接受合法范围（1..100）：输入过程中的空串/0/超范围不落库，避免每次击键都触发
                // 保存失败提示与 WorkManager 重复 schedule（见 PRD 保留份数默认值）。
                value.toIntOrNull()?.takeIf { it in 1..100 }
                    ?.let { onAction(BackupAction.Save(state.settings.copy(retention = it))) }
            },
            keyboardType = KeyboardType.Number,
        )
        ToggleRow(
            label = stringResource(R.string.backup_scheduled),
            checked = state.settings.scheduled,
            onCheckedChange = { onAction(BackupAction.Save(state.settings.copy(scheduled = it))) },
        )
        ToggleRow(
            label = stringResource(R.string.backup_wifi_only),
            checked = state.settings.wifiOnly,
            onCheckedChange = { onAction(BackupAction.Save(state.settings.copy(wifiOnly = it))) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        if (state.snapshots.isEmpty()) {
            EmptyState(stringResource(R.string.backup_snapshots_empty))
        } else {
            state.snapshots.forEach { snapshot ->
                SnapshotRow(
                    snapshot = snapshot,
                    onRestore = { onRequestRestore(snapshot.name) },
                    onDelete = { onRequestDelete(snapshot.name) },
                )
            }
        }
    }
}

@Composable
private fun SnapshotRow(snapshot: RemoteSnapshot, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = snapshot.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.backup_snapshot_size, snapshot.modifiedAt.orEmpty(), formatSize(snapshot.size)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onRestore) { Text(stringResource(R.string.backup_restore)) }
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.backup_delete)) }
        }
    }
}

@Composable
private fun BackupLogSection(entries: List<BackupLogEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(stringResource(R.string.backup_section_log))
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.backup_log_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(if (entry.success) R.string.backup_log_success else R.string.backup_log_failure),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatTimestamp(entry.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportSection(state: BackupUiState, onAction: (BackupAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(stringResource(R.string.backup_section_export))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onAction(BackupAction.ExportCsv) }) {
                Text(stringResource(R.string.backup_export_csv))
            }
            OutlinedButton(onClick = { onAction(BackupAction.ExportJson) }) {
                Text(stringResource(R.string.backup_export_json))
            }
        }
    }
}

@Composable
private fun ImportSection(
    state: BackupUiState,
    preview: CsvPreview,
    onAction: (BackupAction) -> Unit,
    onPickFile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(stringResource(R.string.backup_section_import))
        PresetChips(state.preset) { onAction(BackupAction.SelectPreset(it)) }

        val fieldLabels = CsvField.entries.associateWith { csvFieldLabel(it) }

        Dropdown(
            label = stringResource(R.string.backup_account),
            options = state.accounts.map { it.first to it.second },
            selectedId = state.accountId,
            onSelect = { onAction(BackupAction.SelectAccount(it)) },
        )

        if (state.missingRequired.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.backup_missing_required,
                    state.missingRequired.joinToString("、") { fieldLabels.getValue(it) },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(text = stringResource(R.string.backup_mapping), style = MaterialTheme.typography.titleSmall)
        val columnOptions = listOf("" to stringResource(R.string.backup_column_unset)) +
            preview.headers.mapIndexed { index, header -> index.toString() to "${index + 1}. $header" }
        CsvField.entries.forEach { field ->
            Dropdown(
                label = fieldLabels.getValue(field),
                options = columnOptions,
                selectedId = state.selectedMapping?.columns?.get(field)?.toString().orEmpty(),
                onSelect = { onAction(BackupAction.ChangeColumn(field, it.toIntOrNull())) },
            )
        }

        if (preview.invalidFields.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.backup_invalid_fields,
                    preview.invalidFields.joinToString("、") { fieldLabels.getValue(it) },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionTitle(stringResource(R.string.backup_preview))
        CsvPreviewTable(preview)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onAction(BackupAction.Import) },
                enabled = state.missingRequired.isEmpty() && state.accountId.isNotBlank(),
            ) {
                Text(stringResource(R.string.backup_import_start))
            }
            OutlinedButton(onClick = onPickFile) {
                Text(stringResource(R.string.backup_pick_file))
            }
        }

        state.importReport?.let { report -> ImportReportView(report) }
    }
}

@Composable
private fun CsvPreviewTable(preview: CsvPreview) {
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            preview.headers.forEach { header -> PreviewCell(header, header = true) }
        }
        preview.rows.forEach { row ->
            Row {
                row.forEach { cell -> PreviewCell(cell, header = false) }
            }
        }
    }
}

@Composable
private fun PreviewCell(value: String, header: Boolean) {
    Text(
        text = value,
        modifier = Modifier
            .width(120.dp)
            .padding(4.dp),
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ImportReportView(report: ImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.backup_report, report.inserted, report.duplicates, report.failedLines.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (report.reviewLines.isNotEmpty()) {
            Text(text = stringResource(R.string.backup_report_review_lines, report.reviewLines.joinToString(", ")))
        }
        if (report.failedLines.isNotEmpty()) {
            Text(
                text = stringResource(R.string.backup_report_failed_lines, report.failedLines.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PresetChips(selected: ImportPreset, onSelect: (ImportPreset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ImportPreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { onSelect(preset) },
                label = { Text(importPresetLabel(preset)) },
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Dropdown(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(text = selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelect(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun csvFieldLabel(field: CsvField): String = stringResource(
    when (field) {
        CsvField.TIME -> R.string.backup_field_time
        CsvField.AMOUNT -> R.string.backup_field_amount
        CsvField.TYPE -> R.string.backup_field_type
        CsvField.ACCOUNT -> R.string.backup_field_account
        CsvField.TITLE -> R.string.backup_field_title
        CsvField.REMARK -> R.string.backup_field_remark
    },
)

@Composable
private fun importPresetLabel(preset: ImportPreset): String = stringResource(
    when (preset) {
        ImportPreset.WECHAT -> R.string.backup_preset_wechat
        ImportPreset.ALIPAY -> R.string.backup_preset_alipay
        ImportPreset.GENERIC -> R.string.backup_preset_generic
    },
)

private fun syncNoticeMessage(notice: SyncNotice, detail: String?, context: Context): String = when (notice) {
    SyncNotice.SAVED -> context.getString(R.string.backup_notice_saved)
    SyncNotice.CONNECTION_OK -> context.getString(R.string.backup_notice_connection_ok)
    SyncNotice.CONNECTION_FAILED -> context.getString(R.string.backup_notice_connection_failed)
    SyncNotice.BACKUP_DONE -> context.getString(R.string.backup_notice_backup_done, detail.orEmpty())
    SyncNotice.SNAPSHOTS_REFRESHED -> context.getString(R.string.backup_notice_snapshots_refreshed)
    SyncNotice.RESTORED -> context.getString(R.string.backup_notice_restored)
    SyncNotice.SNAPSHOT_DELETED -> context.getString(R.string.backup_notice_snapshot_deleted)
    SyncNotice.IMPORT_DONE -> context.getString(R.string.backup_notice_import_done)
    SyncNotice.IMPORT_PREVIEW_FAILED -> context.getString(R.string.backup_notice_import_preview_failed)
    SyncNotice.EXPORTED -> context.getString(R.string.backup_notice_exported)
    SyncNotice.EXPORT_FAILED -> context.getString(R.string.backup_notice_export_failed)
    SyncNotice.OPERATION_FAILED -> context.getString(R.string.backup_notice_operation_failed)
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null) return "-"
    val kb = bytes / 1024.0
    return if (kb < 1024) String.format(Locale.US, "%.1f KB", kb) else String.format(Locale.US, "%.1f MB", kb / 1024)
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private const val CSV_MIME = "text/csv"
private const val JSON_MIME = "application/json"
private val FILE_MIME = arrayOf("text/*", "text/csv", "application/csv", "application/octet-stream")

@Preview(showBackground = true)
@Composable
private fun BackupContentPreview() {
    VipLedgerTheme {
        BackupContent(
            state = BackupUiState(
                settings = BackupSettings(endpoint = "https://dav.example.com", username = "user", scheduled = true),
                accounts = listOf("a1" to "现金"),
                accountId = "a1",
                snapshots = listOf(RemoteSnapshot("vipledger-backup-20260928-101500.zip", "Sun, 28 Sep 2026 10:15:00 GMT", 20_480)),
                backupLog = listOf(BackupLogEntry(true, 1_790_208_000_000, "vipledger-backup-20260928-101500.zip")),
                preview = CsvPreview(
                    headers = listOf("交易时间", "金额", "商品"),
                    rows = listOf(listOf("2026-09-01 10:00", "12.50", "咖啡")),
                    mapping = CsvMapping(mapOf(CsvField.TIME to 0, CsvField.AMOUNT to 1, CsvField.TITLE to 2)),
                    invalidFields = emptySet(),
                ),
                selectedMapping = CsvMapping(mapOf(CsvField.TIME to 0, CsvField.AMOUNT to 1, CsvField.TITLE to 2)),
                importReport = ImportReport(inserted = 3, duplicates = 1, failedLines = listOf(7)),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
