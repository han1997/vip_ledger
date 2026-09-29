package com.han1997.vipledger.feature.ledger.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.AmountText
import com.han1997.vipledger.core.designsystem.component.BadgeTone
import com.han1997.vipledger.core.designsystem.component.EmptyState
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.LoadingState
import com.han1997.vipledger.core.designsystem.component.StatusBadge
import com.han1997.vipledger.core.designsystem.component.VipLedgerConfirmDialog
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerSnackbarHost
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.format.fullDateLabel
import com.han1997.vipledger.core.designsystem.format.transactionTypeLabel
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.common.recurrenceFrequencyLabel

/** 周期规则列表页。 */
@Composable
fun RecurringListScreen(
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringListContent(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onCreate = { onCreate(state.ledgerId) },
        onEdit = { ruleId -> onEdit(state.ledgerId, ruleId) },
        modifier = modifier,
    )
}

@Composable
internal fun RecurringListContent(
    state: RecurringListUiState,
    onAction: (RecurringListAction) -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    val noticeMessage = recurringNoticeMessage(notice, state.noticeCount, state.noticeDetail)
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onAction(RecurringListAction.ConsumeNotice)
        }
    }

    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.recurring_title),
                onBack = onBack,
                actions = {
                    TextButton(
                        enabled = !state.generating,
                        onClick = { onAction(RecurringListAction.GenerateNow) },
                    ) {
                        Text(text = stringResource(R.string.recurring_generate_now))
                    }
                },
            )
        },
        snackbarHost = { VipLedgerSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = stringResource(R.string.recurring_add)) },
            )
        },
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
            state.rules.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                EmptyState(message = stringResource(R.string.recurring_empty))
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = state.rules, key = { it.id }) { rule ->
                    RecurringRuleCard(
                        rule = rule,
                        accountName = state.accountNames[rule.accountId].orEmpty(),
                        onEdit = { onEdit(rule.id) },
                        onToggle = { onAction(RecurringListAction.ToggleEnabled(rule)) },
                        onDelete = { onAction(RecurringListAction.RequestDelete(rule)) },
                    )
                }
            }
        }
    }

    state.pendingDelete?.let { rule ->
        VipLedgerConfirmDialog(
            title = stringResource(R.string.recurring_delete_title),
            message = stringResource(R.string.recurring_delete_message, rule.name),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { onAction(RecurringListAction.ConfirmDelete) },
            onDismiss = { onAction(RecurringListAction.DismissDelete) },
            danger = true,
        )
    }
}

@Composable
private fun RecurringRuleCard(
    rule: RecurringRule,
    accountName: String,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = VipLedgerTokens.Space4, vertical = VipLedgerTokens.Space3),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = rule.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = listOf(
                            transactionTypeLabel(rule.txnType),
                            recurrenceFrequencyLabel(rule.freq),
                            accountName,
                        ).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AmountText(amount = rule.amount)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusBadge(
                    text = recurrenceStatusText(rule),
                    tone = when {
                        rule.enabled -> BadgeTone.Brand
                        else -> BadgeTone.Neutral
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                }
            }
        }
    }
}

/** 状态行：停用 / 已走完 / 下次到期日。 */
@Composable
private fun recurrenceStatusText(rule: RecurringRule): String {
    val next = rule.nextOccurrenceAt
    return when {
        !rule.enabled -> stringResource(R.string.recurring_paused)
        next == null -> stringResource(R.string.recurring_finished)
        else -> stringResource(
            R.string.recurring_next_at,
            fullDateLabel(next.toDateColumnLocalDate()),
        )
    }
}

@Composable
internal fun recurringNoticeMessage(
    notice: RecurringNotice?,
    count: Int?,
    detail: String?,
): String? = when (notice) {
    null -> null
    RecurringNotice.GENERATED -> stringResource(R.string.recurring_generated, count ?: 0)
    RecurringNotice.NOTHING_DUE -> stringResource(R.string.recurring_nothing_due)
    RecurringNotice.ENABLED -> stringResource(R.string.recurring_enabled)
    RecurringNotice.PAUSED -> stringResource(R.string.recurring_paused)
    RecurringNotice.DELETED -> stringResource(R.string.notice_deleted)
    RecurringNotice.FAILED ->
        if (!detail.isNullOrBlank()) detail else stringResource(R.string.notice_failed)
}

@Preview(showBackground = true)
@Composable
private fun RecurringListPreview() {
    VipLedgerTheme {
        RecurringListContent(
            state = RecurringListUiState(
                isLoading = false,
                ledgerId = "l1",
                rules = listOf(
                    RecurringRule(
                        id = "r1",
                        ledgerId = "l1",
                        name = "房租",
                        txnType = TransactionType.EXPENSE,
                        amount = Money.ofYuan(2000),
                        accountId = "a1",
                        freq = RecurrenceFrequency.MONTHLY,
                        startAt = 0,
                        nextOccurrenceAt = 0,
                        createdAt = 0,
                        updatedAt = 0,
                    ),
                ),
                accountNames = mapOf("a1" to "招商银行"),
            ),
            onAction = {},
            onBack = {},
            onCreate = {},
            onEdit = {},
        )
    }
}
