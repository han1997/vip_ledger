package com.han1997.vipledger.feature.stats.ui.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.OverviewStat
import com.han1997.vipledger.core.designsystem.component.SegmentOption
import com.han1997.vipledger.core.designsystem.component.SegmentedControl
import com.han1997.vipledger.core.designsystem.component.StatOverviewRow
import com.han1997.vipledger.core.designsystem.component.VipLedgerFab
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.component.chart.BarChart
import com.han1997.vipledger.core.designsystem.component.chart.BarGroup
import com.han1997.vipledger.core.designsystem.component.chart.ChartGeometry
import com.han1997.vipledger.core.designsystem.component.chart.PieChart
import com.han1997.vipledger.core.designsystem.component.chart.PieSlice
import com.han1997.vipledger.core.designsystem.component.chart.percentLabel
import com.han1997.vipledger.core.designsystem.format.dateRangeLabel
import com.han1997.vipledger.core.designsystem.format.rangePresetLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.chartColorOf
import com.han1997.vipledger.core.designsystem.theme.IncomeGreen
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.displayAmount
import com.han1997.vipledger.core.domain.model.PeriodGranularity
import com.han1997.vipledger.core.domain.model.PeriodStat
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.feature.stats.R
import com.han1997.vipledger.feature.stats.navigation.DrillArgs
import com.han1997.vipledger.feature.stats.ui.common.ChoiceChips
import com.han1997.vipledger.feature.stats.ui.common.DateRangePickerDialog
import com.han1997.vipledger.feature.stats.ui.common.FilterOption
import com.han1997.vipledger.feature.stats.ui.common.FilterSection
import com.han1997.vipledger.feature.stats.ui.common.TimeRangeSelector
import com.han1997.vipledger.feature.stats.ui.common.timeRangeSummary
import com.han1997.vipledger.feature.stats.ui.common.writeImageToUri
import com.han1997.vipledger.feature.stats.ui.common.writeTextToUri
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** 统计：时间段 + 粒度 + 维度，占比环图 / 趋势柱状图 + 点一行下钻。 */
@Composable
fun StatsScreen(
    onBack: (() -> Unit)?,
    onAddTxn: (ledgerId: String) -> Unit,
    onOpenSearch: (ledgerId: String) -> Unit,
    onOpenDrill: (DrillArgs) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAddTxn = onAddTxn,
        onOpenSearch = onOpenSearch,
        onOpenDrill = onOpenDrill,
        modifier = modifier,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StatsContent(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    onBack: (() -> Unit)?,
    onAddTxn: (ledgerId: String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenDrill: (DrillArgs) -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val graphicsLayer = rememberGraphicsLayer()
    var pickCustomRange by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf(emptySet<String>()) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var pendingImage by remember { mutableStateOf<ImageBitmap?>(null) }

    val saveCsv = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CSV_MIME),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        val success = uri != null && csv != null && writeTextToUri(context, uri, csv)
        onAction(StatsAction.ExportFinished(success))
    }

    val saveImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(PNG_MIME),
    ) { uri ->
        val image = pendingImage
        pendingImage = null
        val success = uri != null && image != null && writeImageToUri(context, uri, image)
        onAction(StatsAction.ExportFinished(success))
    }

    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
        pendingCsv = export.csv
        saveCsv.launch(export.fileName)
        onAction(StatsAction.ConsumeExport)
    }

    // 统计卡片截图：不同 API 版本差异大，失败就退回「提示改用 CSV」，不阻塞主流程。
    LaunchedEffect(state.pendingImageExport) {
        val fileName = state.pendingImageExport ?: return@LaunchedEffect
        val image = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
        onAction(StatsAction.ConsumeImageExport)
        if (image == null) {
            onAction(StatsAction.ExportFinished(false))
        } else {
            pendingImage = image
            saveImage.launch(fileName)
        }
    }

    val noticeMessage = state.notice?.let { statsNoticeMessage(it) }
    LaunchedEffect(state.notice) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(StatsAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (state.ledgerId.isNotBlank()) {
                VipLedgerFab(
                    onClick = { onAddTxn(state.ledgerId) },
                    contentDescription = stringResource(R.string.stats_add_txn),
                )
            }
        },
        topBar = {
            VipLedgerTopBar(
                title = state.ledgerName.ifBlank { stringResource(R.string.stats_title) },
                onBack = onBack,
                actions = {
                    TextButton(onClick = { onOpenSearch(state.ledgerId) }) {
                        Text(text = stringResource(R.string.search_open))
                    }
                    Box {
                        TextButton(onClick = { exportMenuOpen = true }) {
                            Text(text = stringResource(R.string.stats_export))
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.stats_export_image)) },
                                onClick = {
                                    exportMenuOpen = false
                                    onAction(StatsAction.ExportImage)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.stats_export_csv)) },
                                onClick = {
                                    exportMenuOpen = false
                                    onAction(StatsAction.ExportCsv)
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState()
            }

            // Tab 化后无账本场景：不卡加载态，给明确空态（ledgerId 为空即未选账本）。
            state.ledgerId.isBlank() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(message = stringResource(R.string.stats_no_ledger))
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "conditions") {
                    StatsConditions(
                        state = state,
                        onAction = onAction,
                        onPickCustomRange = { pickCustomRange = true },
                    )
                }

                item(key = "card") {
                    StatisticsCard(
                        state = state,
                        zone = zone,
                        modifier = Modifier.drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                    )
                }

                if (state.dimension != StatsDimension.TIME && !state.isEmpty) {
                    item(key = "drill-hint") {
                        Text(
                            text = stringResource(R.string.stats_drill_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                if (state.isEmpty) {
                    item(key = "empty") {
                        EmptyState(message = stringResource(R.string.stats_empty))
                    }
                } else if (state.dimension != StatsDimension.TIME) {
                    itemsIndexed(
                        items = state.rows,
                        key = { _, row -> rowKey(state.dimension, row) },
                    ) { rowIndex, row ->
                        StatsRowLine(
                            row = row,
                            kind = state.kind,
                            percent = percentLabel(
                                ChartGeometry.fractionOf(row.cents, kindTotalCents(state)),
                            ),
                            color = chartColorOf(rowIndex, state.kind),
                            barFraction = ChartGeometry.fractionOf(row.cents, state.rows.firstOrNull()?.cents ?: kindTotalCents(state)),
                            expanded = rowKey(state.dimension, row) in expandedKeys,
                            onToggleExpand = if (row.children.isEmpty()) {
                                null
                            } else {
                                {
                                    val key = rowKey(state.dimension, row)
                                    expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
                                }
                            },
                            onClick = { drillArgsOf(state, row)?.let(onOpenDrill) },
                        )
                        row.children
                            .takeIf { rowKey(state.dimension, row) in expandedKeys }
                            .orEmpty()
                            .forEachIndexed { index, child ->
                                StatsRowLine(
                                    row = child,
                                    kind = state.kind,
                                    percent = percentLabel(
                                        ChartGeometry.fractionOf(child.cents, kindTotalCents(state)),
                                    ),
                                    color = chartColorOf(rowIndex, state.kind).copy(alpha = 0.7f),
                                    barFraction = ChartGeometry.fractionOf(
                                        child.cents,
                                        state.rows.firstOrNull()?.children?.maxOfOrNull { it.cents } ?: kindTotalCents(state),
                                    ),
                                    expanded = false,
                                    onToggleExpand = null,
                                    onClick = { drillArgsOf(state, child)?.let(onOpenDrill) },
                                    child = true,
                                )
                            }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }

                    if (state.dimension == StatsDimension.TAG) {
                        item(key = "tag-note") {
                            Text(
                                text = stringResource(R.string.stats_tag_share_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickCustomRange) {
        DateRangePickerDialog(
            zone = zone,
            initialRange = state.customRange,
            onDismiss = { pickCustomRange = false },
            onConfirm = { range ->
                pickCustomRange = false
                onAction(StatsAction.SelectCustomRange(range))
            },
        )
    }
}

@Composable
private fun StatsConditions(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    onPickCustomRange: () -> Unit,
) {
    val granularityOptions = PeriodGranularity.entries.map { granularity ->
        FilterOption(id = granularity.name, label = granularityLabel(granularity))
    }
    val dimensionOptions = StatsDimension.entries.map { dimension ->
        FilterOption(id = dimension.name, label = dimensionLabel(dimension))
    }
    val rangeOptions = SEGMENT_PRESETS.map { SegmentOption(it.name, rangePresetLabel(it)) } + SegmentOption(CUSTOM_ID, stringResource(R.string.search_time_custom))
    val selectedRangeId = rangeSegmentId(state.preset, state.customRange)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VipLedgerTokens.Space4, vertical = VipLedgerTokens.Space2),
        verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
    ) {
        SegmentedControl(
            options = rangeOptions,
            selectedId = selectedRangeId,
            onSelect = { id ->
                if (id == CUSTOM_ID) onPickCustomRange()
                else DateRangePreset.entries.firstOrNull { it.name == id }?.let { onAction(StatsAction.SelectPreset(it)) }
            },
            scrollable = true,
        )

        if (state.customRange != null) {
            TextButton(onClick = onPickCustomRange) {
                Text(text = dateRangeLabel(requireNotNull(state.customRange)))
            }
        } else {
            Text(
                text = timeRangeSummary(state.preset, state.range, state.today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilterSection(stringResource(R.string.stats_dimension_label)) {
            ChoiceChips(
                options = dimensionOptions,
                selectedIds = setOf(state.dimension.name),
                onToggle = { id ->
                    StatsDimension.entries.firstOrNull { it.name == id }
                        ?.let { onAction(StatsAction.SelectDimension(it)) }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            FilterSection(stringResource(R.string.stats_granularity_label), Modifier.weight(1f)) {
                ChoiceChips(
                    options = granularityOptions,
                    selectedIds = setOf(state.granularity.name),
                    onToggle = { id ->
                        PeriodGranularity.entries.firstOrNull { it.name == id }
                            ?.let { onAction(StatsAction.SelectGranularity(it)) }
                    },
                )
            }
            if (state.dimension != StatsDimension.TIME) {
                ChoiceChips(
                    options = listOf(
                        FilterOption(CategoryKind.EXPENSE.name, stringResource(R.string.stats_summary_expense)),
                        FilterOption(CategoryKind.INCOME.name, stringResource(R.string.stats_summary_income)),
                    ),
                    selectedIds = setOf(state.kind.name),
                    onToggle = { id ->
                        CategoryKind.entries.firstOrNull { it.name == id }
                            ?.let { onAction(StatsAction.SelectKind(it)) }
                    },
                )
            }
        }
    }
}

/** 「自定义」在分段控件里的伪 id（不是 DateRangePreset）。 */
private const val CUSTOM_ID = "CUSTOM"

/** 统计分段控件当前选中项：有自定义区间优先，否则取三段预设之一，其余归自定义。 */
internal fun rangeSegmentId(preset: DateRangePreset?, customRange: DateRange?): String = when {
    customRange != null -> CUSTOM_ID
    preset != null && preset in SEGMENT_PRESETS -> preset.name
    else -> CUSTOM_ID
}

internal val SEGMENT_PRESETS = listOf(
    DateRangePreset.THIS_WEEK,
    DateRangePreset.THIS_MONTH,
    DateRangePreset.THIS_YEAR,
)

internal const val CUSTOM_SEGMENT_ID = CUSTOM_ID

@Composable
private fun StatisticsCard(
    state: StatsUiState,
    zone: TimeZone,
    modifier: Modifier = Modifier,
) {
    val uncategorized = stringResource(R.string.stats_uncategorized)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VipLedgerTokens.Space4),
        verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space4),
    ) {
        StatOverviewRow(
            stats = listOf(
                OverviewStat(stringResource(R.string.stats_summary_expense), state.expense.toDisplayString(), ExpenseRed),
                OverviewStat(stringResource(R.string.stats_summary_income), state.income.toDisplayString(), IncomeGreen),
                OverviewStat(stringResource(R.string.stats_summary_balance), state.balance.toDisplayString(), MaterialTheme.colorScheme.onSurface),
            ),
        )

        Text(
            text = stringResource(R.string.stats_summary_count, state.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state.dimension) {
            StatsDimension.TIME -> StatCard {
                BarChart(
                    groups = state.periods.map { period ->
                        BarGroup(
                            label = periodLabel(period, state.granularity),
                            incomeCents = period.income.cents,
                            expenseCents = period.expense.cents,
                        )
                    },
                )
            }

            else -> if (state.rows.any { it.cents < 0 }) StatCard {
                BarChart(
                    groups = state.rows.map { row -> BarGroup(row.label ?: uncategorized, 0, row.cents) },
                )
            } else StatCard {
                PieChart(
                    slices = state.rows.mapIndexed { index, row ->
                        PieSlice(
                            label = row.label ?: uncategorized,
                            cents = row.cents,
                            color = chartColorOf(index, state.kind),
                        )
                    },
                    valueText = { slice -> Money.ofCents(slice.cents).toDisplayString() },
                    centerTitle = state.kindTotal.toDisplayString(),
                    centerCaption = stringResource(R.string.stats_total),
                )
            }
        }
    }
}

/** 图表容器：白面 + 发丝边框 + 16dp 圆角（原型 .card）。 */
@Composable
private fun StatCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(VipLedgerTokens.RadiusCard),
        border = androidx.compose.foundation.BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.padding(VipLedgerTokens.Space4)) { content() }
    }
}

@Composable
private fun SummaryAmount(
    label: String,
    amount: Money,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = amount.toDisplayString(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun StatsRowLine(
    row: StatsRow,
    kind: CategoryKind,
    percent: String,
    color: Color,
    barFraction: Float,
    expanded: Boolean,
    onToggleExpand: (() -> Unit)?,
    onClick: () -> Unit,
    child: Boolean = false,
) {
    val label = row.label ?: stringResource(R.string.stats_uncategorized)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (child) 32.dp else 16.dp,
                end = 16.dp,
                top = 11.dp,
                bottom = 11.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.stats_rank_count, row.count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = percent,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.ofCents(row.cents).toDisplayString(),
                style = displayAmount,
                maxLines = 1,
            )
            if (onToggleExpand != null) {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.stats_collapse_row else R.string.stats_expand_row,
                        ),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(VipLedgerTokens.RadiusFull)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(VipLedgerTokens.RadiusFull)),
            )
        }
    }
}

/** 环图占比的分母：当前口径（收入/支出）的合计。 */
private fun kindTotalCents(state: StatsUiState): Long = state.kindTotal.cents

private fun rowKey(dimension: StatsDimension, row: StatsRow): String = when (dimension) {
    StatsDimension.CATEGORY -> row.categoryId ?: "uncategorized-" + row.label
    StatsDimension.TAG -> row.tagId ?: "tag-" + row.label
    StatsDimension.ACCOUNT -> row.accountId ?: "account-" + row.label
    StatsDimension.TIME -> "time-" + row.label
}

private fun drillArgsOf(state: StatsUiState, row: StatsRow): DrillArgs? = when {
    row.categoryId != null -> DrillArgs(
        ledgerId = state.ledgerId,
        categoryId = row.categoryId,
        range = state.range,
    )

    row.tagId != null -> DrillArgs(
        ledgerId = state.ledgerId,
        tagId = row.tagId,
        range = state.range,
    )

    row.accountId != null -> DrillArgs(
        ledgerId = state.ledgerId,
        accountId = row.accountId,
        range = state.range,
    )

    else -> null
}

@Composable
private fun granularityLabel(granularity: PeriodGranularity): String = stringResource(
    when (granularity) {
        PeriodGranularity.DAY -> R.string.stats_granularity_day
        PeriodGranularity.WEEK -> R.string.stats_granularity_week
        PeriodGranularity.MONTH -> R.string.stats_granularity_month
    },
)

@Composable
private fun dimensionLabel(dimension: StatsDimension): String = stringResource(
    when (dimension) {
        StatsDimension.CATEGORY -> R.string.stats_dimension_category
        StatsDimension.TAG -> R.string.stats_dimension_tag
        StatsDimension.ACCOUNT -> R.string.stats_dimension_account
        StatsDimension.TIME -> R.string.stats_dimension_time
    },
)

@Composable
private fun periodLabel(period: PeriodStat, granularity: PeriodGranularity): String = when (granularity) {
    PeriodGranularity.DAY -> stringResource(
        R.string.stats_period_day_label,
        period.start.monthNumber,
        period.start.dayOfMonth,
    )

    PeriodGranularity.WEEK -> stringResource(
        R.string.stats_period_week_label,
        period.start.monthNumber,
        period.start.dayOfMonth,
    )

    PeriodGranularity.MONTH -> stringResource(
        R.string.stats_period_month_label,
        period.start.monthNumber,
    )
}

@Composable
private fun statsNoticeMessage(notice: StatsNotice): String = stringResource(
    when (notice) {
        StatsNotice.EXPORTED -> R.string.stats_exported
        StatsNotice.EXPORT_FAILED -> R.string.stats_export_failed
    },
)

private const val CSV_MIME = "text/csv"
private const val PNG_MIME = "image/png"

@Preview(showBackground = true)
@Composable
private fun StatsContentPreview() {
    val today = LocalDate(2026, 9, 24)
    VipLedgerTheme {
        StatsContent(
            state = StatsUiState(
                isLoading = false,
                ledgerId = "l1",
                ledgerName = "默认账本",
                preset = null,
                range = DateRange(LocalDate(2026, 9, 1), today),
                today = today,
                income = Money.ofCents(800000),
                expense = Money.ofCents(132100),
                count = 12,
                rows = listOf(
                    StatsRow("餐饮", 63200, Money.ofCents(63200), Money.ZERO, 6),
                    StatsRow("交通", 38000, Money.ofCents(38000), Money.ZERO, 3),
                    StatsRow("购物", 30900, Money.ofCents(30900), Money.ZERO, 3),
                ),
            ),
            onAction = {},
            onBack = {},
            onAddTxn = {},
            onOpenSearch = {},
            onOpenDrill = {},
        )
    }
}
