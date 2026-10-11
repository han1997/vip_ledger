package com.han1997.vipledger.feature.ledger.ui.wealth.trend

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.SecondaryButton
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.IncomeGreen
import com.han1997.vipledger.core.designsystem.theme.TransferBlue
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.component.chart.ChartGeometry
import com.han1997.vipledger.core.designsystem.component.chart.LineChart
import com.han1997.vipledger.core.designsystem.component.chart.LineSeries
import com.han1997.vipledger.core.model.time.toLocalDate
import com.han1997.vipledger.feature.ledger.R

/** 资产趋势页（P7）：三条曲线（资产/负债/净资产）+ 区间切换 + CSV 导出。 */
@Composable
fun AssetTrendScreen(onBack: () -> Unit, viewModel: AssetTrendViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssetTrendContent(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
internal fun AssetTrendContent(state: AssetTrendUiState, onAction: (AssetTrendAction) -> Unit, onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedIndex by remember(state.range) { mutableStateOf<Int?>(null) }
    var exportSucceeded by remember { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<TrendCsvExport?>(null) }
    val exportDoneText = stringResource(R.string.wealth_trend_export_done)
    val exportFailedText = stringResource(R.string.wealth_trend_export_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        val ok = uri != null && export != null && writeTextToUri(context, uri, export.csv)
        exportSucceeded = ok
    }

    LaunchedEffect(exportSucceeded) {
        val message = when (exportSucceeded) {
            true -> exportDoneText
            false -> exportFailedText
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            exportSucceeded = null
        }
    }

    VipLedgerScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { VipLedgerTopBar(title = stringResource(R.string.wealth_trend_title), onBack = onBack) },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(VipLedgerTokens.Space4),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
        ) {
            when {
                state.loading -> item { LoadingState() }
                state.failed -> item {
                    EmptyState(
                        message = stringResource(R.string.ledger_load_failed),
                        actionLabel = stringResource(R.string.ledger_retry),
                        onAction = { onAction(AssetTrendAction.Retry) },
                    )
                }
                !state.hasData -> item { EmptyState(message = stringResource(R.string.wealth_trend_empty)) }
                else -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
                        ) {
                            TrendRange.entries.forEach { range ->
                                FilterChip(
                                    selected = state.range == range,
                                    onClick = { onAction(AssetTrendAction.SetRange(range)) },
                                    label = { Text(rangeLabel(range)) },
                                )
                            }
                        }
                    }
                    item {
                        GroupCard {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(VipLedgerTokens.Space4),
                                verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
                            ) {
                                val point = state.pointAt(selectedIndex) ?: state.points.last()
                                Text(
                                    text = fullDateLabel(point.date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    SeriesAmount(stringResource(R.string.wealth_trend_assets), point.assetsCents, IncomeGreen)
                                    SeriesAmount(stringResource(R.string.wealth_trend_debt), point.debtCents, ExpenseRed)
                                    SeriesAmount(stringResource(R.string.wealth_trend_net), point.netWorthCents, TransferBlue)
                                }
                                LineChart(
                                    series = listOf(
                                        LineSeries(points = state.points.map { it.assetsCents }, color = IncomeGreen),
                                        LineSeries(points = state.points.map { it.debtCents }, color = ExpenseRed),
                                        LineSeries(points = state.points.map { it.netWorthCents }, color = TransferBlue),
                                    ),
                                    selectedIndex = selectedIndex,
                                    modifier = Modifier.pointerInput(state.points) {
                                        detectTapGestures { offset ->
                                            val fraction = if (size.width == 0) 0f else offset.x / size.width.toFloat()
                                            selectedIndex = ChartGeometry.guideIndex(fraction, state.points.size)
                                        }
                                    },
                                )
                                LegendRow()
                            }
                        }
                    }
                    item {
                        SecondaryButton(stringResource(R.string.wealth_trend_export_csv), {
                            val export = trendCsvOf(
                                points = state.points,
                                ledgerName = state.ledgerName,
                                range = state.range,
                                today = state.today,
                            )
                            pendingExport = export
                            exportLauncher.launch(export.fileName)
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** 选中某个采样日的数值行。 */
@Composable
private fun SeriesAmount(label: String, cents: Long, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AmountText(com.han1997.vipledger.core.model.money.Money.ofCents(cents), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space4, Alignment.CenterHorizontally),
    ) {
        LegendDot(stringResource(R.string.wealth_trend_assets), IncomeGreen)
        LegendDot(stringResource(R.string.wealth_trend_debt), ExpenseRed)
        LegendDot(stringResource(R.string.wealth_trend_net), TransferBlue)
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun rangeLabel(range: TrendRange): String = stringResource(
    when (range) {
        TrendRange.LAST_30_DAYS -> R.string.wealth_trend_range_30
        TrendRange.LAST_90_DAYS -> R.string.wealth_trend_range_90
        TrendRange.THIS_YEAR -> R.string.wealth_trend_range_year
        TrendRange.ALL -> R.string.wealth_trend_range_all
    },
)

/** SAF 写入：任何异常收敛成 false，由界面提示「导出失败」（import-export.md 约定）。 */
private fun writeTextToUri(context: android.content.Context, uri: android.net.Uri, text: String): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        } != null
    }.getOrDefault(false)