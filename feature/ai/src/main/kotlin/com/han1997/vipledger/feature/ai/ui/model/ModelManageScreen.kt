package com.han1997.vipledger.feature.ai.ui.model

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.format.timeLabel
import com.han1997.vipledger.core.designsystem.saf.readTextFromUri
import com.han1997.vipledger.core.designsystem.saf.writeTextToUri
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ai.R
import kotlinx.datetime.TimeZone

/** 端侧模型管理页（PRD Requirement 6）：查看当前模型、控制自动训练、导入导出。 */
@Composable
fun ModelManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModelManageContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun ModelManageContent(
    state: ModelManageUiState,
    onAction: (ModelManageAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<ModelExport?>(null) }

    // 导出：一次性状态 + SAF CreateDocument（import-export.md 的 M5 流程，不新造机制）。
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JSON_MIME),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        onAction(ModelManageAction.ExportFinished(uri != null && export != null && writeTextToUri(context, uri, export.content)))
    }
    // 导入模型：OpenDocument 读文本 → 交给 VM 用 ModelCodec 严格校验。
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val content = uri?.let { readTextFromUri(context, it) }
        if (content != null) onAction(ModelManageAction.ImportModel(content))
    }

    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
        pendingExport = export
        createDocument.launch(export.fileName)
        onAction(ModelManageAction.ConsumeExport) // 立刻消费，避免重组后重复弹窗
    }

    val noticeMessage = state.notice?.let { modelNoticeText(it, state.noticeDetail) }
    LaunchedEffect(state.notice, state.noticeDetail) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(ModelManageAction.ConsumeNotice)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { VipLedgerTopBar(title = stringResource(R.string.ai_model_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) {
                item(key = "busy") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.ai_model_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "current") { CurrentModelSection(state) }

            item(key = "skipped") {
                SkippedSection(state = state)
            }

            item(key = "autotrain") {
                AutoTrainSection(state = state, onAction = onAction)
            }

            item(key = "samples") {
                SamplesSection(state = state, onAction = onAction)
            }

            item(key = "file") {
                ModelFileSection(
                    state = state,
                    onAction = onAction,
                    onImportClick = { openDocument.launch(IMPORT_MIME_TYPES) },
                )
            }
        }
    }
}

@Composable
private fun CurrentModelSection(state: ModelManageUiState) {
    val zone = remember { TimeZone.currentSystemDefault() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(stringResource(R.string.ai_model_section_current))
        if (!state.hasModel) {
            EmptyState(message = stringResource(R.string.ai_model_none))
            return@Column
        }
        InfoRow(stringResource(R.string.ai_model_version), state.modelVersion)
        // 时间为空（0）时显示占位符，不显示 1970 年。
        val trainedAt = if (state.trainedAt > 0L) {
            fullDateLabel(state.trainedAt.toLocalDate(zone)) + " " + timeLabel(state.trainedAt, zone)
        } else {
            stringResource(R.string.ai_model_unknown)
        }
        InfoRow(stringResource(R.string.ai_model_trained_at), trainedAt)
        InfoRow(stringResource(R.string.ai_model_sample_count), state.modelSampleCount.toString())
        InfoRow(stringResource(R.string.ai_model_label_count), state.labelCount.toString())
        InfoRow(stringResource(R.string.ai_model_feature_version), state.featureVersion.toString())
    }
}

@Composable
private fun SkippedSection(state: ModelManageUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(stringResource(R.string.ai_model_section_skipped))
        Text(
            // 每类阈值（D3#3）：与自动训练触发线（autoTrainThreshold）是两个数，别拿后者来写这句。
            text = stringResource(R.string.ai_model_skipped_hint, state.minSamplesPerClass),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.skippedClasses.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_model_skipped_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // 缺名字时回退显示 id——「哪些分类被跳过」必须可见（PRD D3#3 后果说明）。
            state.skippedClasses.forEach { skipped ->
                val label = skipped.name.ifBlank { skipped.categoryId }
                Text(
                    text = stringResource(R.string.ai_model_skipped_row, label, skipped.sampleCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AutoTrainSection(state: ModelManageUiState, onAction: (ModelManageAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(stringResource(R.string.ai_model_section_autotrain))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.ai_model_autotrain_switch), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.autoTrainEnabled,
                onCheckedChange = { onAction(ModelManageAction.ToggleAutoTrain(it)) },
            )
        }
        Text(
            text = stringResource(R.string.ai_model_autotrain_hint, state.autoTrainThreshold),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.ai_model_untrained, state.untrainedSamples),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onAction(ModelManageAction.TrainNow) },
            enabled = !state.busy && state.ledgerId != null,
        ) {
            Text(stringResource(R.string.ai_model_train_now))
        }
    }
}

@Composable
private fun SamplesSection(state: ModelManageUiState, onAction: (ModelManageAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(stringResource(R.string.ai_model_section_samples))
        InfoRow(stringResource(R.string.ai_model_samples_total), state.totalSamples.toString())
        Text(
            text = stringResource(R.string.ai_model_export_samples_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { onAction(ModelManageAction.ExportSamples) },
            enabled = !state.busy && state.hasSamplesToExport,
        ) {
            Text(stringResource(R.string.ai_model_export_samples))
        }
    }
}

@Composable
private fun ModelFileSection(
    state: ModelManageUiState,
    onAction: (ModelManageAction) -> Unit,
    onImportClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(stringResource(R.string.ai_model_section_file))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { onAction(ModelManageAction.ExportModel) },
                enabled = !state.busy && state.hasModel,
            ) {
                Text(stringResource(R.string.ai_model_export_file))
            }
            OutlinedButton(
                onClick = onImportClick,
                enabled = !state.busy,
            ) {
                Text(stringResource(R.string.ai_model_import_file))
            }
        }
        Text(
            text = stringResource(R.string.ai_model_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun modelNoticeText(notice: ModelNotice, detail: String?): String = when (notice) {
    ModelNotice.TRAINED -> stringResource(R.string.ai_model_notice_trained)
    ModelNotice.TRAIN_NO_SAMPLES -> stringResource(R.string.ai_model_notice_train_no_samples)
    ModelNotice.TRAIN_FAILED -> stringResource(R.string.ai_model_notice_train_failed)
    ModelNotice.IMPORTED -> stringResource(R.string.ai_model_notice_imported)
    ModelNotice.IMPORT_INVALID -> stringResource(R.string.ai_model_notice_import_invalid)
    ModelNotice.IMPORT_FAILED -> stringResource(R.string.ai_model_notice_import_failed)
    ModelNotice.EXPORTED -> stringResource(R.string.ai_model_notice_exported)
    ModelNotice.EXPORT_FAILED -> stringResource(R.string.ai_model_notice_export_failed)
    ModelNotice.EXPORT_NO_SAMPLES -> stringResource(R.string.ai_model_notice_export_no_samples)
    ModelNotice.EXPORT_NO_MODEL -> stringResource(R.string.ai_model_notice_export_no_model)
    ModelNotice.SAVED -> stringResource(R.string.ai_model_notice_saved)
    ModelNotice.FAILED -> if (!detail.isNullOrBlank()) detail else stringResource(R.string.ai_model_notice_failed)
}

private const val JSON_MIME = "application/json"
private val IMPORT_MIME_TYPES = arrayOf("application/json", "application/octet-stream")

@Preview(showBackground = true)
@Composable
private fun ModelManageContentPreview() {
    VipLedgerTheme {
        ModelManageContent(
            state = ModelManageUiState(
                isLoading = false,
                ledgerId = "l1",
                hasModel = true,
                modelVersion = "v1-1695800000000",
                trainedAt = 1_695_800_000_000L,
                featureVersion = 1,
                modelSampleCount = 120,
                labelCount = 6,
                totalSamples = 140,
                untrainedSamples = 20,
                autoTrainEnabled = false,
                skippedClasses = listOf(SkippedClass("cat-travel", "旅行", 2)),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
