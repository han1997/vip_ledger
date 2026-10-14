package com.han1997.vipledger.feature.ledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han1997.vipledger.core.designsystem.component.ActionRow
import com.han1997.vipledger.core.designsystem.component.GroupCard
import com.han1997.vipledger.core.designsystem.component.VipLedgerScaffold
import com.han1997.vipledger.core.designsystem.component.VipLedgerTopBar
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.serifTitle
import com.han1997.vipledger.core.designsystem.theme.vipLedgerAccentColors
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
    onOpenImportBill: () -> Unit = {},
    ledgerName: String = "",
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
            contentPadding = PaddingValues(
                start = VipLedgerTokens.Space4,
                end = VipLedgerTokens.Space4,
                top = VipLedgerTokens.Space2,
                bottom = VipLedgerTokens.Space6,
            ),
            verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space2),
        ) {
            item { ProfileHero(ledgerName = ledgerName, pendingDraftCount = pendingDraftCount) }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_ledger),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.ledger_manage_title), Icons.Filled.Book, onOpenLedgers),
                        SettingsRow(stringResource(R.string.account_manage_title), Icons.Filled.AccountBalanceWallet, onOpenAccounts, ledgerReady),
                        SettingsRow(stringResource(R.string.category_manage_title), Icons.Filled.Label, onOpenCategories, ledgerReady),
                        SettingsRow(stringResource(R.string.tag_manage_title), Icons.Filled.CreditCard, onOpenTags, ledgerReady),
                    ),
                )
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_plans),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.home_recurring), Icons.Filled.EventNote, onOpenRecurring, ledgerReady),
                        SettingsRow(
                            label = stringResource(R.string.draft_title),
                            icon = Icons.Filled.EventNote,
                            onClick = onOpenDrafts,
                            enabled = ledgerReady,
                            badgeText = if (pendingDraftCount > 0) stringResource(R.string.draft_count, pendingDraftCount) else null,
                        ),
                        SettingsRow(stringResource(R.string.home_loans), Icons.Filled.CreditCard, onOpenLoans, ledgerReady),
                    ),
                )
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_ai),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.auto_title), Icons.Filled.AutoAwesome, onOpenAutoBook),
                        SettingsRow(stringResource(R.string.settings_ai_model), Icons.Filled.Build, onOpenAiModel),
                        SettingsRow(stringResource(R.string.settings_ai_rules), Icons.Filled.Build, onOpenAiRules, ledgerReady),
                    ),
                )
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_data),
                    rows = listOf(
                        SettingsRow(stringResource(R.string.billimport_entry), Icons.Filled.Upload, onOpenImportBill),
                        SettingsRow(stringResource(R.string.home_backup), Icons.Filled.Backup, onOpenBackup),
                    ),
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

/** 个人信息 hero(原型 .profile):墨绿渐变 + 金色头像 + 当前账本。 */
@Composable
private fun ProfileHero(ledgerName: String, pendingDraftCount: Int) {
    val colors = vipLedgerAccentColors()
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(colors.heroGradientStart, colors.heroGradientEnd)),
                shape = shape,
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space4),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    brush = Brush.linearGradient(listOf(colors.accentAmber, colors.accentCoral)),
                    shape = RoundedCornerShape(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // 原型 .profile .av：金色渐变底 + 深墨绿衬线单字。
            Text(
                text = stringResource(R.string.settings_profile_mark),
                style = serifTitle,
                fontSize = 23.sp,
                color = colors.onAccentAmber,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onHero,
            )
            val subtitle = buildString {
                append(stringResource(R.string.settings_current_ledger, ledgerName.ifBlank { stringResource(R.string.home_no_ledger) }))
                if (pendingDraftCount > 0) append(" · " + stringResource(R.string.draft_count, pendingDraftCount))
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onHeroMuted,
            )
        }
    }
}

private data class SettingsRow(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val badgeText: String? = null,
)

@Composable
private fun SettingsGroup(
    title: String,
    rows: List<SettingsRow>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space1)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = VipLedgerTokens.Space1, top = VipLedgerTokens.Space4, bottom = VipLedgerTokens.Space2),
        )
        GroupCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActionRow(
                    title = row.label,
                    leadingIcon = row.icon,
                    leadingIconTint = MaterialTheme.colorScheme.primary,
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
            appVersion = InstalledAppVersion("0.1.10", 11),
            ledgerName = "日常账本",
            pendingDraftCount = 2,
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