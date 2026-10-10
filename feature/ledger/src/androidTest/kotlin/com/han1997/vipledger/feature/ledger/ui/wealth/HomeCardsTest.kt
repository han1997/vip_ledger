package com.han1997.vipledger.feature.ledger.ui.wealth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.HomeCard
import com.han1997.vipledger.core.domain.model.HomeCardLayout
import com.han1997.vipledger.core.domain.model.HomeOverview
import com.han1997.vipledger.feature.ledger.R
import com.han1997.vipledger.feature.ledger.ui.home.HomeContent
import com.han1997.vipledger.feature.ledger.ui.home.HomeUiState
import org.junit.Rule
import org.junit.Test

class HomeCardsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun reorderingHidingAndResettingCardsKeepsTheCompositionValid() {
        var state by mutableStateOf(HomeUiState(isLoading = false, ledgerId = "l", ledgerName = "Test", overview = HomeOverview("l", "Test")))
        compose.setContent { VipLedgerTheme {
            HomeContent(state, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}, {})
        } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val savings = context.getString(R.string.wealth_savings)
        repeat(3) {
            compose.runOnIdle { state = state.copy(cards = HomeCardLayout.move(state.cards, HomeCard.SAVINGS, -2)) }
            compose.onNodeWithText(savings).assertExists()
            compose.runOnIdle { state = state.copy(cards = state.cards.map { it.copy(visible = false) }) }
            compose.onNodeWithText(savings).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.home_add_txn)).assertExists()
            compose.runOnIdle { state = state.copy(cards = HomeCardLayout.defaults()) }
        }
    }
}
