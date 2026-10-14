package com.han1997.vipledger.feature.ledger.ui.billimport

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.PrimaryButton
import com.han1997.vipledger.core.designsystem.component.SecondaryButton
import com.han1997.vipledger.core.designsystem.component.SectionHeader
import com.han1997.vipledger.core.designsystem.component.SegmentOption
import com.han1997.vipledger.core.designsystem.component.SegmentedControl
import com.han1997.vipledger.core.designsystem.component.VipLedgerConfirmDialog
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.saf.readBytesFromUri
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.amountColorOf
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportPreview
import com.han1997.vipledger.core.domain.usecase.importbill.BillImportReport
import com.han1997.vipledger.core.domain.usecase.importbill.PendingBillAccount
import com.han1997.vipledger.core.domain.usecase.importbill.PendingBillCategory
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toLocalDateTime
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.TimeZone

/** 一木记账 `.xls` / `.zip` 账单导入页(R9):选文件 → 预览 → 危险确认 → 落库 → 报告。 */
@Composable
fun ImportBillScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportBillViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ImportBillContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun ImportBillContent(
    state: ImportBillUiState,
    onAction: (ImportBillAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF 一次性:「选文件」点按 → 系统选择器 → 读回字节。VM 只收字节,不碰 Uri。
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bytes = uri?.let { readBytesFromUri(context, it) }
        if (bytes != null) onAction(ImportBillAction.FilePicked(bytes)) else onAction(ImportBillAction.FilePickFailed)
    }

    // 危险确认:导入当前账本且账本已有流水(R3);确认后才真正开跑。
    var showConfirm by remember { mutableStateOf(false) }
    val beginImport = {
        if (state.currentNeedsConfirm && state.target == ImportTargetChoice.CURRENT_LEDGER) {
            showConfirm = true
        } else {
            onAction(ImportBillAction.StartImport)
        }
    }

    val noticeMessage = state.notice?.let { importNoticeMessage(it, context) }
    LaunchedEffect(state.notice) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(ImportBillAction.ConsumeNotice)
        }
    }

    if (showConfirm) {
        VipLedgerConfirmDialog(
            title = stringResource(R.string.billimport_confirm_current_title),
            message = stringResource(R.string.billimport_confirm_current_message),
            confirmLabel = stringResource(R.string.billimport_confirm_continue),
            dismissLabel = stringResource(R.string.billimport_dismiss),
            onConfirm = {
                showConfirm = false
                onAction(ImportBillAction.StartImport)
            },
            onDismiss = { showConfirm = false },
            danger = true,
        )
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { VipLedgerTopBar(title = stringResource(R.string.billimport_title), onBack = onBack) },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = VipLedgerTokens.Space4,
                end = VipLedgerTokens.Space4,
                top = VipLedgerTokens.Space2,
                bottom = VipLedgerTokens.Space6,
            ),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            state.error?.let { error ->
                item(key = "error") {
                    Text(
                        text = stringResource(importErrorMessage(error)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            val report = state.report
            val preview = state.preview
            when {
                state.importing -> item(key = "progress") {
                    ImportProgressSection(
                        processed = state.progress?.done ?: 0,
                        total = state.progress?.total ?: 0,
                    )
                }

                report != null -> item(key = "report") {
                    ImportReportSection(
                        report = report,
                        onReset = { onAction(ImportBillAction.Reset) },
                    )
                }

                state.parsing -> item(key = "parsing") {
                    Text(
                        text = stringResource(R.string.billimport_processing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                preview != null -> item(key = "preview") {
                    ImportPreviewSection(
                        state = state,
                        preview = preview,
                        onAction = onAction,
                        onPickFile = { pickFile.launch(IMPORT_MIME_TYPES) },
                        onStartImport = beginImport,
                    )
                }

                else -> item(key = "empty") {
                    ImportEmptySection(onPickFile = { pickFile.launch(IMPORT_MIME_TYPES) })
                }
            }
        }
    }
}

/** 空态:还没有选择账单文件(原型 import-empty)。 */
@Composable
private fun ImportEmptySection(onPickFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = VipLedgerTokens.Space6),
        verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space4),
    ) {
        EmptyState(
            message = stringResource(R.string.billimport_pick_hint),
            icon = Icons.Filled.Upload,
            actionLabel = stringResource(R.string.billimport_pick_file),
            onAction = onPickFile,
        )
        Text(
            text = stringResource(R.string.billimport_pick_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.billimport_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 预览态:识别摘要 → 落点二选一 → 将新建清单 → 开始导入(原型 import-preview)。 */
@Composable
private fun ImportPreviewSection(
    state: ImportBillUiState,
    preview: BillImportPreview,
    onAction: (ImportBillAction) -> Unit,
    onPickFile: () -> Unit,
    onStartImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space4)) {
        GroupCard {
            SummaryRow(
                title = stringResource(R.string.billimport_recognized_source),
                subtitle = stringResource(R.string.billimport_recognized),
                value = stringResource(R.string.billimport_rows_count, preview.dataRowCount),
            )
            SummaryRow(
                title = stringResource(R.string.billimport_data_rows),
                subtitle = previewRangeLabel(preview.startAt, preview.endAt),
                value = stringResource(R.string.billimport_rows_count, preview.dataRowCount),
            )
            SummaryRow(
                title = stringResource(R.string.billimport_expense_total),
                value = Money.ofCents(preview.expenseCents).toDisplayString(),
                valueColor = amountColorOf(TransactionType.EXPENSE),
            )
            SummaryRow(
                title = stringResource(R.string.billimport_income_total),
                value = Money.ofCents(preview.incomeCents).toDisplayString(),
                valueColor = amountColorOf(TransactionType.INCOME),
            )
            SummaryRow(
                title = stringResource(R.string.billimport_net_total),
                value = Money.ofCents(preview.netCents).toDisplayString(),
                valueColor = amountColorOf(
                    if (preview.netCents >= 0) TransactionType.INCOME else TransactionType.EXPENSE,
                ),
            )
        }

        if (preview.skippedRows.isNotEmpty()) {
            Text(
                text = stringResource(R.string.billimport_skipped, preview.skippedRows.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (preview.failedRows.isNotEmpty()) {
            Text(
                text = stringResource(R.string.billimport_failed, preview.failedRows.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.billimport_failed_rows, preview.failedRows.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader(title = stringResource(R.string.billimport_target_title))
        SegmentedControl(
            options = listOf(
                SegmentOption(TARGET_NEW_ID, stringResource(R.string.billimport_target_new, state.newLedgerName)),
                SegmentOption(TARGET_CURRENT_ID, stringResource(R.string.billimport_target_current, currentLedgerLabel(state))),
            ),
            selectedId = if (state.target == ImportTargetChoice.CURRENT_LEDGER) TARGET_CURRENT_ID else TARGET_NEW_ID,
            onSelect = { id ->
                onAction(
                    ImportBillAction.SelectTarget(
                        if (id == TARGET_CURRENT_ID) ImportTargetChoice.CURRENT_LEDGER else ImportTargetChoice.NEW_LEDGER,
                    ),
                )
            },
            scrollable = true,
        )

        PendingList(
            title = stringResource(R.string.billimport_new_categories),
            expanded = state.showNewCategories,
            labels = state.newCategories.map { it.displayLabel() },
            countText = stringResource(R.string.billimport_list_count, state.newCategories.size),
            onToggle = { onAction(ImportBillAction.ToggleCategories) },
        )
        PendingList(
            title = stringResource(R.string.billimport_new_accounts),
            expanded = state.showNewAccounts,
            labels = state.newAccounts.map { it.displayLabel() },
            countText = stringResource(R.string.billimport_list_count, state.newAccounts.size),
            onToggle = { onAction(ImportBillAction.ToggleAccounts) },
        )

        Text(
            text = stringResource(R.string.billimport_dedup_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(
            text = stringResource(R.string.billimport_start),
            onClick = onStartImport,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.billimport_reparse),
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 进行中态:进度条 + 已处理 x / N 行(原型 import-progress)。 */
@Composable
private fun ImportProgressSection(processed: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
        Text(
            text = stringResource(R.string.billimport_processing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { if (total > 0) processed.toFloat() / total.toFloat() else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.billimport_progress, processed, total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 报告态:新增/重复/失败/跳过/新建分类/新建账户(原型 import-report)。 */
@Composable
private fun ImportReportSection(report: BillImportReport, onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space4)) {
        Text(
            text = stringResource(R.string.billimport_report_title, report.inserted, report.duplicates),
            style = MaterialTheme.typography.titleMedium,
        )
        GroupCard {
            SummaryRow(
                stringResource(R.string.billimport_report_inserted),
                value = report.inserted.toString(),
                valueColor = amountColorOf(TransactionType.INCOME),
            )
            SummaryRow(stringResource(R.string.billimport_report_duplicates), value = report.duplicates.toString())
            SummaryRow(stringResource(R.string.billimport_report_failed), value = report.failedRows.size.toString())
            SummaryRow(stringResource(R.string.billimport_report_skipped), value = report.skippedRows.size.toString())
            SummaryRow(stringResource(R.string.billimport_report_new_categories), value = report.newCategoryCount.toString())
            SummaryRow(stringResource(R.string.billimport_report_new_accounts), value = report.newAccountCount.toString())
        }
        Text(
            text = stringResource(R.string.billimport_report_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryButton(
            text = stringResource(R.string.billimport_reparse),
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingList(
    title: String,
    expanded: Boolean,
    labels: List<String>,
    countText: String,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
        SectionHeader(title = title, actionLabel = countText, onActionClick = onToggle)
        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
                verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
            ) {
                labels.forEach { label -> TagChip(label) }
            }
        }
    }
}

/** 只读标签胶囊(原型 .tagchip):分类 / 账户名。 */
@Composable
private fun TagChip(label: String) {
    Text(
        text = label,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryRow(
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
        )
    }
}

@Composable
private fun currentLedgerLabel(state: ImportBillUiState): String =
    state.currentLedgerName.ifBlank { stringResource(R.string.home_no_ledger) }

/** 预览的日期区间:「2026-10-08 ~ 2026-10-08」;无有效行时退回空串。 */
@Composable
private fun previewRangeLabel(startAt: Long?, endAt: Long?): String {
    if (startAt == null || endAt == null) return ""
    val zone = TimeZone.currentSystemDefault()
    return stringResource(
        R.string.billimport_lines_summary,
        fullDateLabel(startAt.toLocalDateTime(zone).date),
        fullDateLabel(endAt.toLocalDateTime(zone).date),
    )
}

/** 「将新建的分类 / 账户」清单项展示文本。 */
private fun PendingBillCategory.displayLabel(): String = "$parentName · $name"

private fun PendingBillAccount.displayLabel(): String = name

private fun importErrorMessage(error: ImportError): Int = when (error) {
    ImportError.READ_FAILED -> R.string.billimport_error_read
    ImportError.UNSUPPORTED_FORMAT -> R.string.billimport_error_format
    ImportError.IMPORT_FAILED -> R.string.billimport_error_import
}

private fun importNoticeMessage(notice: ImportNotice, context: Context): String = when (notice) {
    ImportNotice.DONE -> context.getString(R.string.billimport_notice_done)
    ImportNotice.EXISTING_LEDGER -> context.getString(R.string.billimport_notice_existing_ledger)
}

private const val TARGET_NEW_ID = "new"
private const val TARGET_CURRENT_ID = "current"

/** Provider 报的 MIME 不可靠,按扩展名放开常见类型,真实格式仍由解析器按魔数判定。 */
private val IMPORT_MIME_TYPES = arrayOf(
    "application/octet-stream",
    "application/vnd.ms-excel",
    "application/zip",
    "application/x-zip-compressed",
)

@Preview(showBackground = true)
@Composable
private fun ImportBillEmptyPreview() {
    VipLedgerTheme {
        ImportBillContent(state = ImportBillUiState(), onAction = {}, onBack = {})
    }
}