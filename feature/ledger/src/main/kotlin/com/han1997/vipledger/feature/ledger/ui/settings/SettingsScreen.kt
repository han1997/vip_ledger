package com.han1997.vipledger.feature.ledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.han1997.vipledger.core.designsystem.component.ActionRow
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.SectionHeader
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.feature.ledger.R

@Composable
fun SettingsScreen(
    ledgerReady: Boolean,
    onBack: () -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenLoans: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VipLedgerScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VipLedgerTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(VipLedgerTokens.Space4),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space4),
        ) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_ledger),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.ledger_manage_title), onOpenLedgers),
                        SettingsRow(stringResource(R.string.account_manage_title), onOpenAccounts, ledgerReady),
                        SettingsRow(stringResource(R.string.category_manage_title), onOpenCategories, ledgerReady),
                        SettingsRow(stringResource(R.string.tag_manage_title), onOpenTags, ledgerReady),
                    ),
                )
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_tools),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.home_stats), onOpenStats, ledgerReady),
                        SettingsRow(stringResource(R.string.home_backup), onOpenBackup),
                        SettingsRow(stringResource(R.string.home_ai), onOpenAi),
                    ),
                )
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_plans),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.home_recurring), onOpenRecurring, ledgerReady),
                        SettingsRow(stringResource(R.string.draft_title), onOpenDrafts, ledgerReady),
                        SettingsRow(stringResource(R.string.home_loans), onOpenLoans, ledgerReady),
                    ),
                )
            }
        }
    }
}

private data class SettingsRow(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun SettingsGroup(
    title: String,
    rows: List<SettingsRow>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2)) {
        SectionHeader(title = title)
        GroupCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActionRow(
                    title = row.label,
                    enabled = row.enabled,
                    onClick = row.onClick.takeIf { row.enabled },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    VipLedgerTheme {
        SettingsScreen(
            ledgerReady = true,
            onBack = {},
            onOpenLedgers = {},
            onOpenAccounts = {},
            onOpenCategories = {},
            onOpenTags = {},
            onOpenStats = {},
            onOpenBackup = {},
            onOpenAi = {},
            onOpenRecurring = {},
            onOpenDrafts = {},
            onOpenLoans = {},
        )
    }
}
