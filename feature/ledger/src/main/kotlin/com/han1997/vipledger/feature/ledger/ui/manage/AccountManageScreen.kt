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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.ConfirmDialog
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.LabeledTextField
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.accountTypeLabel
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.manageNoticeMessage

/** 账户管理页。 */
@Composable
fun AccountManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountManageContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun AccountManageContent(
    state: AccountManageUiState,
    onAction: (AccountManageAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    val noticeMessage = if (notice == null) null else manageNoticeMessage(notice, state.noticeDetail)
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(AccountManageAction.ConsumeNotice)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.account_manage_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAction(AccountManageAction.OpenCreate) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = stringResource(R.string.account_manage_add)) },
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
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FilterChip(
                    selected = state.showArchived,
                    onClick = { onAction(AccountManageAction.ToggleShowArchived) },
                    label = { Text(text = stringResource(R.string.account_manage_show_archived)) },
                )
            }
            if (state.accounts.isEmpty()) {
                item { EmptyState(message = stringResource(R.string.empty_accounts)) }
            }
            items(items = state.accounts, key = { it.id }) { account ->
                AccountCard(account = account, onAction = onAction)
            }
        }
    }

    state.editor?.let { editor ->
        AccountEditorDialog(
            editor = editor,
            onAction = onAction,
        )
    }

    state.pendingDelete?.let { account ->
        ConfirmDialog(
            title = stringResource(R.string.manage_delete_asset_title),
            message = stringResource(R.string.manage_delete_asset_message, account.name),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { onAction(AccountManageAction.ConfirmDelete) },
            onDismiss = { onAction(AccountManageAction.DismissDelete) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(account: Account, onAction: (AccountManageAction) -> Unit) {
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = account.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = accountTypeLabel(account.type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(
                            if (account.isDebt) R.string.account_manage_debt else R.string.account_manage_balance,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        amount = if (account.isDebt) account.debtAmount else account.currentBalance,
                        color = if (account.isDebt) ExpenseRed else Color.Unspecified,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (account.isArchived) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = stringResource(R.string.ledger_manage_archived_badge)) },
                    )
                }
                TextButton(onClick = { onAction(AccountManageAction.OpenEdit(account)) }) {
                    Text(text = stringResource(R.string.common_edit))
                }
                TextButton(
                    onClick = {
                        onAction(AccountManageAction.SetArchived(account.id, !account.isArchived))
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (account.isArchived) R.string.ledger_manage_unarchive else R.string.ledger_manage_archive,
                        ),
                    )
                }
                TextButton(onClick = { onAction(AccountManageAction.RequestDelete(account)) }) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorDialog(
    editor: AccountEditor,
    onAction: (AccountManageAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(AccountManageAction.DismissEditor) },
        title = {
            Text(
                text = stringResource(
                    if (editor.accountId == null) R.string.account_manage_add else R.string.account_manage_open_editor,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledTextField(
                    label = stringResource(R.string.account_manage_name),
                    value = editor.name,
                    onValueChange = { onAction(AccountManageAction.UpdateEditorName(it)) },
                )
                Text(
                    text = stringResource(R.string.account_manage_type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { type ->
                        FilterChip(
                            selected = editor.type == type,
                            onClick = { onAction(AccountManageAction.UpdateEditorType(type)) },
                            label = { Text(text = accountTypeLabel(type)) },
                        )
                    }
                }
                LabeledTextField(
                    label = stringResource(R.string.account_manage_initial_balance),
                    value = editor.initialBalanceInput,
                    onValueChange = { onAction(AccountManageAction.UpdateInitialBalance(it)) },
                    keyboardType = KeyboardType.Decimal,
                    placeholder = stringResource(R.string.txn_edit_amount_placeholder),
                )
                if (editor.type == AccountType.CREDIT_CARD || editor.type == AccountType.LOAN) {
                    LabeledTextField(
                        label = stringResource(R.string.account_manage_credit_limit),
                        value = editor.creditLimitInput,
                        onValueChange = { onAction(AccountManageAction.UpdateCreditLimit(it)) },
                        keyboardType = KeyboardType.Decimal,
                    )
                    LabeledTextField(
                        label = stringResource(R.string.account_manage_billing_day),
                        value = editor.billingDayInput,
                        onValueChange = { onAction(AccountManageAction.UpdateBillingDay(it)) },
                        keyboardType = KeyboardType.Number,
                    )
                    LabeledTextField(
                        label = stringResource(R.string.account_manage_due_day),
                        value = editor.dueDayInput,
                        onValueChange = { onAction(AccountManageAction.UpdateDueDay(it)) },
                        keyboardType = KeyboardType.Number,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.account_manage_include_net_worth),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = editor.includeInNetWorth,
                        onCheckedChange = { onAction(AccountManageAction.UpdateIncludeInNetWorth(it)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(AccountManageAction.SaveEditor) }) {
                Text(text = stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(AccountManageAction.DismissEditor) }) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccountManageContentPreview() {
    VipLedgerTheme {
        AccountManageContent(
            state = AccountManageUiState(
                isLoading = false,
                accounts = listOf(
                    Account(
                        id = "a1",
                        ledgerId = "l1",
                        name = "现金",
                        type = AccountType.CASH,
                        currentBalance = Money.ofCents(12345),
                        createdAt = 0,
                        updatedAt = 0,
                    ),
                    Account(
                        id = "a2",
                        ledgerId = "l1",
                        name = "招行信用卡",
                        type = AccountType.CREDIT_CARD,
                        currentBalance = Money.ofCents(-250000),
                        createdAt = 0,
                        updatedAt = 0,
                    ),
                ),
            ),
            onAction = {},
            onBack = {},
        )
    }
}