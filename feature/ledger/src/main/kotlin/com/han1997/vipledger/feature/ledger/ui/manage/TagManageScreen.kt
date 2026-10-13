package com.han1997.vipledger.feature.ledger.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.han1997.vipledger.core.designsystem.component.VipLedgerConfirmDialog
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.manageNoticeMessage

/** 标签管理页。 */
@Composable
fun TagManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TagManageContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun TagManageContent(
    state: TagManageUiState,
    onAction: (TagManageAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    val noticeMessage = if (notice == null) null else manageNoticeMessage(notice, state.noticeDetail)
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(TagManageAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.tag_manage_title),
                onBack = onBack,
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            VipLedgerFab(
                onClick = { onAction(TagManageAction.OpenCreate) },
                contentDescription = stringResource(R.string.tag_manage_add),
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.tags.isEmpty()) {
                    item { EmptyState(message = stringResource(R.string.empty_tags)) }
                }
                items(items = state.tags, key = { it.id }) { tag ->
                    TagCard(tag = tag, onAction = onAction)
                }
            }
        }
    }

    state.editor?.let { editor ->
        AlertDialog(
            onDismissRequest = { onAction(TagManageAction.DismissEditor) },
            title = {
                Text(
                    text = stringResource(
                        if (editor.tagId == null) R.string.tag_manage_add else R.string.tag_manage_edit_title,
                    ),
                )
            },
            text = {
                LabeledTextField(
                    label = stringResource(R.string.tag_manage_name),
                    value = editor.name,
                    onValueChange = { onAction(TagManageAction.UpdateName(it)) },
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(TagManageAction.SaveEditor) }) {
                    Text(text = stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(TagManageAction.DismissEditor) }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.pendingDelete?.let { tag ->
        VipLedgerConfirmDialog(
            title = stringResource(R.string.manage_delete_tag_title),
            message = stringResource(R.string.manage_delete_tag_message),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { onAction(TagManageAction.ConfirmDelete) },
            onDismiss = { onAction(TagManageAction.DismissDelete) },
            danger = true,
        )
    }
}

@Composable
private fun TagCard(tag: Tag, onAction: (TagManageAction) -> Unit) {
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = VipLedgerTokens.Space4, end = VipLedgerTokens.Space2, top = VipLedgerTokens.Space1, bottom = VipLedgerTokens.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onAction(TagManageAction.OpenEdit(tag)) }) {
                Text(text = stringResource(R.string.common_edit))
            }
            TextButton(onClick = { onAction(TagManageAction.RequestDelete(tag)) }) {
                Text(text = stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TagManageContentPreview() {
    VipLedgerTheme {
        TagManageContent(
            state = TagManageUiState(
                isLoading = false,
                tags = listOf(
                    Tag(id = "t1", ledgerId = "l1", name = "报销", createdAt = 0, updatedAt = 0),
                ),
            ),
            onAction = {},
            onBack = {},
        )
    }
}
