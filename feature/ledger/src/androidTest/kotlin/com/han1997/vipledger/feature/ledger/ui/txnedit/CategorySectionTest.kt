package com.han1997.vipledger.feature.ledger.ui.txnedit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.model.CategoryKind
import org.junit.Rule
import org.junit.Test

/** 真正执行动态节点插入，覆盖纯UiState单测无法发现的Composer节点错位。 */
class CategorySectionTest {
    @get:Rule val compose = createComposeRule()
    private fun category(id: String, name: String, parent: String? = null) = Category(id, "l1", parent, name, CategoryKind.EXPENSE, createdAt = 0, updatedAt = 0)

    @Test fun expandingSwitchingAndCollapsingParentsKeepsTheNodeTreeValid() {
        val food = CategoryNode(category("food", "Food"), listOf(category("lunch", "Lunch", "food")))
        val travel = CategoryNode(category("travel", "Travel"), listOf(category("train", "Train", "travel")))
        val home = CategoryNode(category("home", "Home"), listOf(category("rent", "Rent", "home")))
        val health = CategoryNode(category("health", "Health"), listOf(category("doctor", "Doctor", "health")))
        var state by mutableStateOf(TxnEditUiState(isLoading = false, categoryNodes = listOf(food, travel, home, health)))
        compose.setContent {
            VipLedgerTheme {
                Column {
                    CategorySection(state) { action ->
                        if (action is TxnEditAction.ToggleCategoryParent) {
                            state = state.copy(expandedCategoryId = if (state.expandedCategoryId == action.categoryId) null else action.categoryId)
                        }
                    }
                }
            }
        }
        repeat(3) {
            compose.onNodeWithText("Food").performClick()
            compose.onNodeWithText("Lunch").assertIsDisplayed()
            compose.onNodeWithText("Health").performClick()
            compose.onNodeWithText("Doctor").assertIsDisplayed()
            compose.onNodeWithText("Lunch").assertDoesNotExist()
            compose.onNodeWithText("Travel").performClick()
            compose.onNodeWithText("Train").assertIsDisplayed()
            compose.onNodeWithText("Doctor").assertDoesNotExist()
            compose.onNodeWithText("Travel").performClick()
            compose.onNodeWithText("Train").assertDoesNotExist()
        }
    }
}
