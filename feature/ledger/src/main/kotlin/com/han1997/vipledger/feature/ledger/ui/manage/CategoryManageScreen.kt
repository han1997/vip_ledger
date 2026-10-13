package com.han1997.vipledger.feature.ledger.ui.manage

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.VipLedgerFab
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.SectionHeader
import com.han1997.vipledger.core.designsystem.component.VipLedgerConfirmDialog
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.categoryKindLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.manageNoticeMessage

/** 分类管理页：三个方向各自一棵两级树。 */
@Composable
fun CategoryManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryManageContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun CategoryManageContent(
    state: CategoryManageUiState,
    onAction: (CategoryManageAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    val noticeMessage = if (notice == null) null else manageNoticeMessage(notice, state.noticeDetail)
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(CategoryManageAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.category_manage_title),
                onBack = onBack,
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            VipLedgerFab(
                onClick = { onAction(CategoryManageAction.OpenCreateTop(CategoryKind.EXPENSE)) },
                contentDescription = stringResource(R.string.category_manage_add_top),
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
                if (state.nodes.isEmpty()) {
                    item { EmptyState(message = stringResource(R.string.empty_categories)) }
                }
                CategoryKind.entries.forEach { kind ->
                    val nodes = state.nodesOf(kind)
                    if (nodes.isEmpty()) return@forEach
                    item(key = "header-" + kind.name) {
                        SectionHeader(
                            title = categoryKindLabel(kind),
                            actionLabel = stringResource(R.string.category_manage_add_top),
                            onActionClick = { onAction(CategoryManageAction.OpenCreateTop(kind)) },
                        )
                    }
                    items(
                        count = nodes.size,
                        key = { index -> nodes[index].id },
                    ) { index ->
                        CategoryCard(node = nodes[index], onAction = onAction)
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        CategoryEditorDialog(editor = editor, onAction = onAction)
    }

    state.pendingDelete?.let { category ->
        val childCount = state.nodes.firstOrNull { it.id == category.id }?.children?.size ?: 0
        VipLedgerConfirmDialog(
            title = stringResource(R.string.manage_delete_category_title),
            message = if (childCount > 0) {
                stringResource(R.string.manage_delete_category_top_message, category.name)
            } else {
                stringResource(R.string.manage_delete_category_leaf_message, category.name)
            },
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { onAction(CategoryManageAction.ConfirmDelete) },
            onDismiss = { onAction(CategoryManageAction.DismissDelete) },
            danger = true,
        )
    }
}

@Composable
private fun CategoryCard(node: CategoryNode, onAction: (CategoryManageAction) -> Unit) {
    GroupCard {
        Column(modifier = Modifier.padding(vertical = VipLedgerTokens.Space2)) {
            CategoryRow(
                name = node.name,
                trailing = stringResource(R.string.category_manage_child_count, node.children.size),
                onAddChild = { onAction(CategoryManageAction.OpenCreateChild(node.category)) },
                onEdit = { onAction(CategoryManageAction.OpenEdit(node.category)) },
                onDelete = { onAction(CategoryManageAction.RequestDelete(node.category)) },
            )
            node.children.forEach { child ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                CategoryRow(
                    name = child.name,
                    trailing = null,
                    indent = true,
                    onAddChild = null,
                    onEdit = { onAction(CategoryManageAction.OpenEdit(child)) },
                    onDelete = { onAction(CategoryManageAction.RequestDelete(child)) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    name: String,
    trailing: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    indent: Boolean = false,
    onAddChild: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 32.dp else 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onAddChild != null) {
            TextButton(onClick = onAddChild) {
                Text(text = stringResource(R.string.category_manage_add_child))
            }
        }
        TextButton(onClick = onEdit) {
            Text(text = stringResource(R.string.common_edit))
        }
        TextButton(onClick = onDelete) {
            Text(text = stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    editor: CategoryEditor,
    onAction: (CategoryManageAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(CategoryManageAction.DismissEditor) },
        title = {
            Text(
                text = stringResource(
                    when {
                        editor.categoryId != null -> R.string.category_manage_edit_title
                        editor.isChild -> R.string.category_manage_add_child
                        else -> R.string.category_manage_add_top
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledTextField(
                    label = stringResource(R.string.category_manage_name),
                    value = editor.name,
                    onValueChange = { onAction(CategoryManageAction.UpdateName(it)) },
                )
                if (!editor.isChild) {
                    Text(
                        text = stringResource(R.string.category_manage_kind),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryKind.entries.forEach { kind ->
                            FilterChip(
                                selected = editor.kind == kind,
                                onClick = { onAction(CategoryManageAction.UpdateKind(kind)) },
                                label = { Text(text = categoryKindLabel(kind)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(CategoryManageAction.SaveEditor) }) {
                Text(text = stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(CategoryManageAction.DismissEditor) }) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun CategoryManageContentPreview() {
    VipLedgerTheme {
        CategoryManageContent(
            state = CategoryManageUiState(
                isLoading = false,
                nodes = listOf(
                    CategoryNode(
                        category = Category(
                            id = "c1",
                            ledgerId = "l1",
                            name = "餐饮",
                            kind = CategoryKind.EXPENSE,
                            isSystem = true,
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                        children = listOf(
                            Category(
                                id = "c2",
                                ledgerId = "l1",
                                parentId = "c1",
                                name = "午餐",
                                kind = CategoryKind.EXPENSE,
                                isSystem = true,
                                createdAt = 0,
                                updatedAt = 0,
                            ),
                        ),
                    ),
                ),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
