package com.han1997.vipledger.feature.ledger.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import com.han1997.vipledger.core.designsystem.component.ConfirmDialog
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.manageNoticeMessage

/** 账本管理页。 */
@Composable
fun LedgerManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LedgerManageContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun LedgerManageContent(
    state: LedgerManageUiState,
    onAction: (LedgerManageAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    val noticeMessage = if (notice == null) null else manageNoticeMessage(notice, state.noticeDetail)
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(LedgerManageAction.ConsumeNotice)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.ledger_manage_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAction(LedgerManageAction.OpenCreate) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = stringResource(R.string.ledger_manage_add)) },
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
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.ledgers.isEmpty()) {
                    item { EmptyState(message = stringResource(R.string.home_no_ledger)) }
                }
                items(items = state.ledgers, key = { it.id }) { ledger ->
                    LedgerCard(
                        ledger = ledger,
                        isCurrent = ledger.id == state.currentLedgerId,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        AlertDialog(
            onDismissRequest = { onAction(LedgerManageAction.DismissEditor) },
            title = {
                Text(
                    text = stringResource(
                        if (editor.ledgerId == null) R.string.ledger_manage_add else R.string.common_edit,
                    ),
                )
            },
            text = {
                LabeledTextField(
                    label = stringResource(R.string.ledger_manage_name),
                    value = editor.name,
                    onValueChange = { onAction(LedgerManageAction.UpdateEditorName(it)) },
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(LedgerManageAction.SaveEditor) }) {
                    Text(text = stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(LedgerManageAction.DismissEditor) }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.pendingDelete?.let { ledger ->
        ConfirmDialog(
            title = stringResource(R.string.manage_delete_ledger_title),
            message = stringResource(R.string.manage_delete_ledger_message, ledger.name),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { onAction(LedgerManageAction.ConfirmDelete) },
            onDismiss = { onAction(LedgerManageAction.DismissDelete) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerCard(
    ledger: Ledger,
    isCurrent: Boolean,
    onAction: (LedgerManageAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = ledger.name, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ledger.isDefault) {
                        AssistChip(
                            onClick = {},
                            label = { Text(text = stringResource(R.string.ledger_manage_default_badge)) },
                        )
                    }
                    if (isCurrent) {
                        AssistChip(
                            onClick = {},
                            label = { Text(text = stringResource(R.string.ledger_manage_current)) },
                        )
                    }
                    if (ledger.isArchived) {
                        AssistChip(
                            onClick = {},
                            label = { Text(text = stringResource(R.string.ledger_manage_archived_badge)) },
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!ledger.isArchived && !isCurrent) {
                    TextButton(onClick = { onAction(LedgerManageAction.Select(ledger.id)) }) {
                        Text(text = stringResource(R.string.home_switch_ledger))
                    }
                }
                if (!ledger.isDefault) {
                    TextButton(onClick = { onAction(LedgerManageAction.SetDefault(ledger.id)) }) {
                        Text(text = stringResource(R.string.ledger_manage_set_default))
                    }
                }
                TextButton(
                    onClick = {
                        onAction(
                            LedgerManageAction.SetArchived(ledger.id, !ledger.isArchived),
                        )
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (ledger.isArchived) R.string.ledger_manage_unarchive else R.string.ledger_manage_archive,
                        ),
                    )
                }
                TextButton(onClick = { onAction(LedgerManageAction.OpenEdit(ledger)) }) {
                    Text(text = stringResource(R.string.common_edit))
                }
                if (!ledger.isDefault) {
                    TextButton(onClick = { onAction(LedgerManageAction.RequestDelete(ledger)) }) {
                        Text(
                            text = stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LedgerManageContentPreview() {
    VipLedgerTheme {
        LedgerManageContent(
            state = LedgerManageUiState(
                isLoading = false,
                currentLedgerId = "l1",
                ledgers = listOf(
                    Ledger(id = "l1", name = "默认账本", isDefault = true, createdAt = 0, updatedAt = 0),
                    Ledger(id = "l2", name = "旅行", createdAt = 0, updatedAt = 0),
                ),
            ),
            onAction = {},
            onBack = {},
        )
    }
}