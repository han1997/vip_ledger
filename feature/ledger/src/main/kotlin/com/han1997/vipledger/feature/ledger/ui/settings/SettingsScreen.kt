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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
    appVersion: InstalledAppVersion,
    pendingDraftCount: Int = 0,
    onBack: (() -> Unit)?,
    onOpenLedgers: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAiModel: () -> Unit,
    onOpenAiRules: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenAutoBook: () -> Unit = {},
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
                    title = stringResource(R.string.settings_group_plans),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.home_recurring), onOpenRecurring, ledgerReady),
                        SettingsRow(
                            label = stringResource(R.string.draft_title),
                            onClick = onOpenDrafts,
                            enabled = ledgerReady,
                            badgeText = if (pendingDraftCount > 0) {
                                stringResource(R.string.draft_count, pendingDraftCount)
                            } else {
                                null
                            },
                        ),
                        SettingsRow(stringResource(R.string.home_loans), onOpenLoans, ledgerReady),
                    ),
                )
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_ai),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.auto_title), onOpenAutoBook),
                        SettingsRow(stringResource(R.string.settings_ai_model), onOpenAiModel),
                        SettingsRow(stringResource(R.string.settings_ai_rules), onOpenAiRules, ledgerReady),
                    ),
                )
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_data),
                    rows = listOf(SettingsRow(stringResource(R.string.home_backup), onOpenBackup)),
                )
            }
            item(key = "app-version") {
                Text(
                    text = stringResource(R.string.settings_app_version, appVersion.name, appVersion.code),
                    modifier = Modifier.fillMaxWidth().padding(vertical = VipLedgerTokens.Space2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class SettingsRow(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val badgeText: String? = null,
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
                    trailingText = row.badgeText,
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
            appVersion = InstalledAppVersion("0.1.1", 2),
            onBack = {},
            onOpenLedgers = {},
            onOpenAccounts = {},
            onOpenCategories = {},
            onOpenTags = {},
            onOpenBackup = {},
            onOpenAiModel = {},
            onOpenAiRules = {},
            onOpenRecurring = {},
            onOpenDrafts = {},
            onOpenLoans = {},
        )
    }
}
