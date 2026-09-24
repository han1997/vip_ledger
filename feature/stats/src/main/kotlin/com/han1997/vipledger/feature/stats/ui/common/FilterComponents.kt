package com.han1997.vipledger.feature.stats.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.format.dateRangeLabel
import com.han1997.vipledger.core.designsystem.format.rangePresetLabel
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.DateRangePreset
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import com.han1997.vipledger.feature.stats.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * 搜索页与下钻页共用的条件控件。
 *
 * 全部控件都是无状态优先：状态从参数传入、变化通过回调抛出，
 * 这样 Compose 测试可以直接渲染，也不会有两个页面各自实现一套多选逻辑。
 */

/** 带标题的条件分组。 */
@Composable
fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** 多选胶囊：一行放不下会自动折行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(
    options: List<FilterOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.id in selectedIds,
                onClick = { onToggle(option.id) },
                label = { Text(text = option.label) },
            )
        }
    }
}

/**
 * 时间段选择：不限时间 + 10 个预设 + 自定义区间（二选一，与领域层的 [SearchQuery] 一致）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeRangeSelector(
    preset: DateRangePreset?,
    customRange: DateRange?,
    onSelectPreset: (DateRangePreset?) -> Unit,
    onPickCustomRange: () -> Unit,
    modifier: Modifier = Modifier,
    includeAll: Boolean = true,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (includeAll) {
            FilterChip(
                selected = preset == null && customRange == null,
                onClick = { onSelectPreset(null) },
                label = { Text(text = rangePresetLabel(null)) },
            )
        }
        DateRangePreset.entries.forEach { entry ->
            FilterChip(
                selected = preset == entry,
                onClick = { onSelectPreset(entry) },
                label = { Text(text = rangePresetLabel(entry)) },
            )
        }
        FilterChip(
            selected = customRange != null,
            onClick = onPickCustomRange,
            label = {
                Text(
                    text = customRange?.let { dateRangeLabel(it) }
                        ?: stringResource(R.string.search_time_custom),
                )
            },
        )
    }
}

/** 金额区间（按绝对值过滤）；输入非法时只标红提示，不改变查询。 */
@Composable
fun AmountRangeFields(
    minText: String,
    maxText: String,
    isInvalid: Boolean,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LabeledTextField(
            label = stringResource(R.string.search_amount_min),
            value = minText,
            onValueChange = onMinChange,
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Decimal,
            isError = isInvalid,
        )
        LabeledTextField(
            label = stringResource(R.string.search_amount_max),
            value = maxText,
            onValueChange = onMaxChange,
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Decimal,
        )
    }
    if (isInvalid) {
        Text(
            text = stringResource(R.string.search_amount_invalid),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 时间条件的摘要文案，给统计页顶部的条件区用。 */
@Composable
fun timeRangeSummary(preset: DateRangePreset?, range: DateRange?, today: LocalDate): String = when {
    preset != null -> rangePresetLabel(preset) + " · " + dateRangeLabel(preset.resolve(today))
    range != null -> dateRangeLabel(range)
    else -> stringResource(R.string.stats_time_all)
}

/**
 * 自定义时间段选择：Material3 的区间选择器外观是 UTC 零点毫秒，
 * 与设备时区的「某一天」互转统一走 `toDateColumnLocalDate` / `toDateColumnEpochMillis`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    zone: TimeZone,
    onDismiss: () -> Unit,
    onConfirm: (DateRange) -> Unit,
    initialRange: DateRange? = null,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange?.start?.toDateColumnEpochMillis(),
        initialSelectedEndDateMillis = initialRange?.endInclusive?.toDateColumnEpochMillis(),
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis ?: start
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (start != null && end != null) {
                        onConfirm(
                            DateRange(start.toDateColumnLocalDate(), end.toDateColumnLocalDate()),
                        )
                    }
                },
                enabled = start != null,
            ) {
                Text(text = stringResource(R.string.stats_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.stats_action_cancel))
            }
        },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.height(PICKER_HEIGHT),
            showModeToggle = false,
        )
    }
}

private val PICKER_HEIGHT = 480.dp