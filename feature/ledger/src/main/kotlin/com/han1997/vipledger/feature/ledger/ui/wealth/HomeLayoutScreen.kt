package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.han1997.vipledger.core.designsystem.component.*
import com.han1997.vipledger.core.designsystem.theme.*
import com.han1997.vipledger.core.domain.model.HomeCard
import com.han1997.vipledger.feature.ledger.R

@Composable
fun homeCardTitle(card: HomeCard): String = stringResource(when (card) {
    HomeCard.BUDGET -> R.string.ledger_home_budget_entry
    HomeCard.TODAY -> R.string.ledger_home_today
    HomeCard.SAVINGS -> R.string.wealth_savings
    HomeCard.WEEK -> R.string.ledger_home_week
    HomeCard.PENDING -> R.string.ledger_home_pending
})
@Composable
fun HomeLayoutScreen(onBack: () -> Unit, viewModel: HomeLayoutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeLayoutContent(state, viewModel::onAction, onBack)
}
@Composable
private fun HomeLayoutContent(state: HomeLayoutUiState, onAction: (HomeLayoutAction) -> Unit, onBack: () -> Unit) {
    VipLedgerScaffold(topBar = { VipLedgerTopBar(title = stringResource(R.string.wealth_home_layout), onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(VipLedgerTokens.Space4), verticalArrangement = Arrangement.spacedBy(VipLedgerTokens.Space3)) {
            item { Text(stringResource(R.string.wealth_layout_hint), style = MaterialTheme.typography.bodySmall) }
            if (state.failed) item { Text(stringResource(R.string.wealth_write_failed), color = MaterialTheme.colorScheme.error) }
            items(state.rows, key = { it.card }) { row -> GroupCard {
                ActionRow(title = homeCardTitle(row.card), checked = row.visible, onCheckedChange = { onAction(HomeLayoutAction.Toggle(row.card, it)) }, enabled = !state.busy)
                Row {
                    IconButton(onClick = { onAction(HomeLayoutAction.Move(row.card, -1)) }, enabled = !state.busy && row.position > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.wealth_move_up)) }
                    IconButton(onClick = { onAction(HomeLayoutAction.Move(row.card, 1)) }, enabled = !state.busy && row.position < state.rows.lastIndex) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.wealth_move_down)) }
                }
            } }
            item { SecondaryButton(stringResource(R.string.wealth_layout_reset), { onAction(HomeLayoutAction.Reset) }, Modifier.fillMaxWidth(), enabled = !state.busy) }
        }
    }
}
@Preview(showBackground = true)
@Composable
private fun HomeLayoutPreview() { VipLedgerTheme { HomeLayoutContent(HomeLayoutUiState(), {}, {}) } }
