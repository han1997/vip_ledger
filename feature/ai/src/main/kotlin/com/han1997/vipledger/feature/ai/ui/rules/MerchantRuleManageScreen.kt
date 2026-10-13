package com.han1997.vipledger.feature.ai.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.VipLedgerFab
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.VipLedgerConfirmDialog
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.usecase.rule.isValidRuleRegex
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField
import com.han1997.vipledger.feature.ai.R
import com.han1997.vipledger.core.designsystem.R as DesignSystemR

/** 商户规则管理页：查看 / 新增 / 编辑 / 启停 / 删除规则（PRD Requirement 9）。 */
@Composable
fun MerchantRuleManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MerchantRuleManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MerchantRuleManageContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun MerchantRuleManageContent(
    state: MerchantRuleManageUiState,
    onAction: (MerchantRuleAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeText = state.notice?.let { ruleNoticeText(it, state.noticeDetail) }

    LaunchedEffect(state.notice, state.noticeDetail) {
        if (noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            onAction(MerchantRuleAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { VipLedgerTopBar(title = stringResource(R.string.ai_rule_title), onBack = onBack) },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            VipLedgerFab(
                onClick = { onAction(MerchantRuleAction.OpenCreate) },
                contentDescription = stringResource(R.string.ai_rule_add),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.rules.isEmpty()) {
                    item(key = "empty") { EmptyState(message = stringResource(R.string.ai_rule_empty)) }
                }
                items(items = state.rules, key = { it.rule.id }) { row ->
                    RuleCard(row = row, onAction = onAction)
                }
            }
        }
    }

    state.editor?.let { editor ->
        RuleEditorDialog(editor = editor, categories = state.categories, onAction = onAction)
    }

    state.pendingDelete?.let { row ->
        VipLedgerConfirmDialog(
            title = stringResource(R.string.ai_rule_delete_title),
            message = stringResource(R.string.ai_rule_delete_message, row.rule.pattern),
            confirmLabel = stringResource(R.string.ai_rule_delete),
            dismissLabel = stringResource(R.string.ai_rule_cancel),
            onConfirm = { onAction(MerchantRuleAction.ConfirmDelete) },
            onDismiss = { onAction(MerchantRuleAction.DismissDelete) },
            danger = true,
        )
    }
}

@Composable
private fun RuleCard(row: MerchantRuleRow, onAction: (MerchantRuleAction) -> Unit) {
    val rule = row.rule
    val uncategorized = stringResource(DesignSystemR.string.common_uncategorized)
    val categoryName = row.categoryName.ifBlank { uncategorized }
    val summary = stringResource(
        R.string.ai_rule_summary,
        stringResource(matchTypeLabelRes(rule.matchType)),
        stringResource(targetFieldLabelRes(rule.targetField)),
        stringResource(R.string.ai_rule_priority_label, rule.priority),
    )

    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceBadge(source = rule.source)
                    Text(text = rule.pattern, style = MaterialTheme.typography.titleMedium)
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onAction(MerchantRuleAction.ToggleEnabled(rule)) },
                )
            }

            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!rule.enabled) {
                Text(
                    text = stringResource(R.string.ai_rule_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.ai_rule_hit_count, row.hitCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAction(MerchantRuleAction.OpenEdit(rule)) }) {
                        Text(stringResource(R.string.ai_rule_edit))
                    }
                    TextButton(onClick = { onAction(MerchantRuleAction.RequestDelete(rule)) }) {
                        Text(
                            text = stringResource(R.string.ai_rule_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** 来源徽标：手写 / 内置 / 学习 用语义色区分，使学习规则一眼可辨、便于审查清理。 */
@Composable
private fun SourceBadge(source: RuleSource) {
    val color: Color = when (source) {
        RuleSource.USER -> MaterialTheme.colorScheme.primary
        RuleSource.BUILTIN -> MaterialTheme.colorScheme.secondary
        RuleSource.LEARNED -> MaterialTheme.colorScheme.tertiary
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(sourceLabelRes(source)),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorDialog(
    editor: MerchantRuleEditor,
    categories: List<RuleCategory>,
    onAction: (MerchantRuleAction) -> Unit,
) {
    val error = editor.error()
    // 新建时全空会立刻报「关键词不能为空」，太吵；只在用户开始输入或编辑既有规则时提示。
    val showError = error != null && (editor.pattern.isNotBlank() || editor.isEditing)
    val patternInvalidRegex = editor.pattern.isNotBlank() &&
        editor.matchType == RuleMatchType.REGEX &&
        !isValidRuleRegex(editor.pattern.trim())

    AlertDialog(
        onDismissRequest = { onAction(MerchantRuleAction.DismissEditor) },
        title = {
            Text(
                stringResource(
                    if (editor.isEditing) R.string.ai_rule_editor_edit_title else R.string.ai_rule_editor_create_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledTextField(
                    label = stringResource(R.string.ai_rule_field_pattern),
                    value = editor.pattern,
                    onValueChange = { onAction(MerchantRuleAction.UpdatePattern(it)) },
                    singleLine = true,
                    isError = patternInvalidRegex,
                    supportingText = if (patternInvalidRegex) error?.message else null,
                )

                FieldGroup(label = stringResource(R.string.ai_rule_field_match_type)) {
                    RuleMatchType.entries.forEach { type ->
                        FilterChip(
                            selected = editor.matchType == type,
                            onClick = { onAction(MerchantRuleAction.UpdateMatchType(type)) },
                            label = { Text(stringResource(matchTypeLabelRes(type))) },
                        )
                    }
                }

                FieldGroup(label = stringResource(R.string.ai_rule_field_target_field)) {
                    RuleTargetField.entries.forEach { field ->
                        FilterChip(
                            selected = editor.targetField == field,
                            onClick = { onAction(MerchantRuleAction.UpdateTargetField(field)) },
                            label = { Text(stringResource(targetFieldLabelRes(field))) },
                        )
                    }
                }

                CategoryDropdown(
                    label = stringResource(R.string.ai_rule_field_category),
                    categories = categories,
                    selectedId = editor.categoryId,
                    onSelect = { onAction(MerchantRuleAction.UpdateCategory(it)) },
                )

                LabeledTextField(
                    label = stringResource(R.string.ai_rule_field_priority),
                    value = if (editor.priority == 0) "" else editor.priority.toString(),
                    onValueChange = { input -> onAction(MerchantRuleAction.UpdatePriority(input.toIntOrNull() ?: 0)) },
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                )

                ToggleRow(
                    label = stringResource(R.string.ai_rule_field_enabled),
                    checked = editor.enabled,
                    onCheckedChange = { onAction(MerchantRuleAction.UpdateEnabled(it)) },
                )

                if (editor.canPromote) {
                    ToggleRow(
                        label = stringResource(R.string.ai_rule_promote),
                        checked = editor.promoteToUser,
                        onCheckedChange = { onAction(MerchantRuleAction.UpdatePromoteToUser(it)) },
                    )
                    Text(
                        text = stringResource(R.string.ai_rule_promote_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (showError && !patternInvalidRegex) {
                    Text(
                        text = error?.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(MerchantRuleAction.SaveEditor) },
                enabled = error == null,
            ) {
                Text(stringResource(R.string.ai_rule_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MerchantRuleAction.DismissEditor) }) {
                Text(stringResource(R.string.ai_rule_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun CategoryDropdown(
    label: String,
    categories: List<RuleCategory>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(text = selectedName ?: stringResource(R.string.ai_rule_pick_category))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            expanded = false
                            onSelect(category.id)
                        },
                    )
                }
            }
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

private fun sourceLabelRes(source: RuleSource): Int = when (source) {
    RuleSource.USER -> R.string.ai_rule_source_user
    RuleSource.BUILTIN -> R.string.ai_rule_source_builtin
    RuleSource.LEARNED -> R.string.ai_rule_source_learned
}

private fun matchTypeLabelRes(type: RuleMatchType): Int = when (type) {
    RuleMatchType.CONTAINS -> R.string.ai_rule_match_contains
    RuleMatchType.REGEX -> R.string.ai_rule_match_regex
    RuleMatchType.EQUALS -> R.string.ai_rule_match_equals
}

private fun targetFieldLabelRes(field: RuleTargetField): Int = when (field) {
    RuleTargetField.TITLE -> R.string.ai_rule_target_title
    RuleTargetField.REMARK -> R.string.ai_rule_target_remark
    RuleTargetField.ANY -> R.string.ai_rule_target_any
}

@Composable
private fun ruleNoticeText(notice: RuleNotice, detail: String?): String = when (notice) {
    RuleNotice.SAVED -> stringResource(R.string.ai_rule_notice_saved)
    RuleNotice.DELETED -> stringResource(R.string.ai_rule_notice_deleted)
    RuleNotice.TOGGLED -> stringResource(R.string.ai_rule_notice_toggled)
    RuleNotice.FAILED -> if (!detail.isNullOrBlank()) detail else stringResource(R.string.ai_rule_notice_failed)
}

@Preview(showBackground = true)
@Composable
private fun MerchantRuleManageContentPreview() {
    VipLedgerTheme {
        MerchantRuleManageContent(
            state = MerchantRuleManageUiState(
                isLoading = false,
                ledgerId = "l1",
                rules = listOf(
                    MerchantRuleRow(
                        rule = MerchantRule(
                            id = "r1",
                            ledgerId = "l1",
                            pattern = "午饭",
                            categoryId = "c_food",
                            priority = 110,
                            source = RuleSource.LEARNED,
                            hitCount = 3,
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                        categoryName = "餐饮",
                    ),
                    MerchantRuleRow(
                        rule = MerchantRule(
                            id = "r2",
                            ledgerId = "l1",
                            pattern = "打车",
                            matchType = RuleMatchType.CONTAINS,
                            categoryId = "c_traffic",
                            priority = 100,
                            source = RuleSource.USER,
                            enabled = false,
                            hitCount = 0,
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                        categoryName = "交通",
                    ),
                ),
                categories = listOf(RuleCategory("c_food", "餐饮"), RuleCategory("c_traffic", "交通")),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
