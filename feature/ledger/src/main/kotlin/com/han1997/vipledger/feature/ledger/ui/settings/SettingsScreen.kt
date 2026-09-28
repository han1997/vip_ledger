package com.han1997.vipledger.feature.ledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = row.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = row.enabled, onClick = row.onClick)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (row.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
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
