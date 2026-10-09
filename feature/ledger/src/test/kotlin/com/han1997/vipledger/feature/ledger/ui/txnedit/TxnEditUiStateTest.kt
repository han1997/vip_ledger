package com.han1997.vipledger.feature.ledger.ui.txnedit

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 目标账户的默认选中规则。
 *
 * 回归用例：M2 模拟器走查时「转账」保存直接失败，原因是切到转账后
 * `toAccountId` 仍是 null（界面虽列出转入账户但没预选），领域层校验直接拒绝。
 */
class TxnEditUiStateTest {

    private fun account(id: String, type: AccountType = AccountType.CASH) = Account(
        id = id,
        ledgerId = LEDGER_ID,
        name = id,
        type = type,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun stateOf(
        type: TransactionType,
        accountId: String? = "cash",
        toAccountId: String? = null,
    ) = TxnEditUiState(
        isLoading = false,
        ledgerId = LEDGER_ID,
        type = type,
        accountId = accountId,
        toAccountId = toAccountId,
        accounts = listOf(
            account("cash"),
            account("cmb", AccountType.BANK_DEBIT),
            account("card", AccountType.CREDIT_CARD),
        ),
        todayMillis = 0L,
        yesterdayMillis = 0L,
    )

    @Test
    fun transferDefaultsToFirstAccountOtherThanSource() {
        assertEquals("cmb", stateOf(TransactionType.TRANSFER).resolvedTargetAccountId())
    }

    @Test
    fun transferKeepsExplicitChoice() {
        val state = stateOf(TransactionType.TRANSFER, toAccountId = "card")
        assertEquals("card", state.resolvedTargetAccountId())
    }

    @Test
    fun transferDropsChoiceThatEqualsSourceAccount() {
        val state = stateOf(TransactionType.TRANSFER, accountId = "cmb", toAccountId = "cmb")
        assertEquals("cash", state.resolvedTargetAccountId())
    }

    @Test
    fun compensateOnlyOffersDebtAccounts() {
        val state = stateOf(TransactionType.DEBT_REPAY)
        assertEquals(listOf("card"), state.targetCandidates.map { it.id })
        assertEquals("card", state.resolvedTargetAccountId())
    }

    @Test
    fun compensateWithoutDebtAccountHasNoCandidate() {
        val state = TxnEditUiState(
            isLoading = false,
            ledgerId = LEDGER_ID,
            type = TransactionType.DEBT_REPAY,
            accountId = "cash",
            accounts = listOf(account("cash"), account("cmb", AccountType.BANK_DEBIT)),
        )
        assertEquals(emptyList(), state.targetCandidates)
        assertNull(state.resolvedTargetAccountId())
    }

    @Test
    fun typesWithoutTargetAccountStayNull() {
        val state = stateOf(TransactionType.EXPENSE, toAccountId = "cmb")
        assertNull(state.resolvedTargetAccountId())
    }

    @Test
    fun aiModeCanBeActiveBeforeTheUserTypes() {
        assertFalse(TxnEditUiState().isAiMode)
        assertTrue(TxnEditUiState(aiMode = true).isAiMode)
    }

    @Test
    fun newEntryRestoresLastAccountWhenStillAvailable() {
        val state = stateOf(TransactionType.EXPENSE, accountId = null)
        assertEquals("card", state.resolvedAccountId("card"))
    }

    @Test
    fun newEntryFallsBackWhenLastAccountIsUnavailable() {
        val state = stateOf(TransactionType.EXPENSE, accountId = null)
        assertEquals("cash", state.resolvedAccountId("deleted"))
    }

    @Test
    fun expandingParentSelectsItAndMarksTheParentRow() {
        val state = categoryState().toggleCategoryParent("food")
        assertEquals("food", state.categoryId)
        assertEquals("food", state.expandedCategoryId)
        assertEquals("food", state.categoryParentId)
    }

    @Test
    fun tappingTheExpandedParentAgainOnlyCollapses() {
        val expanded = categoryState().toggleCategoryParent("food")
        val collapsed = expanded.toggleCategoryParent("food")
        assertNull(collapsed.expandedCategoryId)
        // 收起不清空已选分类，避免误触把分类丢掉
        assertEquals("food", collapsed.categoryId)
    }

    @Test
    fun switchingParentReplacesExpansionAndSelection() {
        val state = categoryState().toggleCategoryParent("food").toggleCategoryParent("travel")
        assertEquals("travel", state.categoryId)
        assertEquals("travel", state.expandedCategoryId)
    }

    @Test
    fun selectingChildKeepsParentExpandedAndNamesBothLevels() {
        val state = categoryState()
            .toggleCategoryParent("food")
            .copy(categoryId = "breakfast")
        assertEquals("food", state.categoryParentId)
        assertEquals(listOf("餐饮", "早餐"), state.selectedCategoryNames)
    }

    @Test
    fun unknownParentIdIsIgnored() {
        val state = categoryState().toggleCategoryParent("missing")
        assertNull(state.categoryId)
        assertNull(state.expandedCategoryId)
    }

    @Test
    fun visibleCategoriesOnlyExposeTheCurrentKind() {
        val state = categoryState()
        assertEquals(listOf("food", "travel"), state.visibleCategories.map { it.category.id })
        val income = state.copy(type = TransactionType.INCOME)
        assertEquals(listOf("salary"), income.visibleCategories.map { it.category.id })
    }

    @Test
    fun busyWhileSavingOrCreatingTagBlocksDuplicateSubmit() {
        assertFalse(TxnEditUiState().isBusy)
        assertTrue(TxnEditUiState(isSaving = true).isBusy)
        assertTrue(TxnEditUiState(isCreatingTag = true).isBusy)
    }

    @Test
    fun inputMatchesExistingTagByNameSoItIsReusedNotDuplicated() {
        val state = TxnEditUiState(tags = listOf(tag("t1", "出差")))
        assertEquals("t1", state.copy(tagInput = " 出差 ").existingTag?.id)
        assertNull(state.copy(tagInput = "出差报销").existingTag)
    }

    @Test
    fun matchingTagsSearchesAllTagsByPartialName() {
        val state = TxnEditUiState(tags = listOf(tag("t1", "出差"), tag("t2", "报销")))
        assertEquals(listOf("t2"), state.copy(tagInput = "报").matchingTags.map { it.id })
        assertEquals(listOf("t1", "t2"), state.matchingTags.map { it.id })
    }

    private fun tag(id: String, name: String) = Tag(
        id = id,
        ledgerId = LEDGER_ID,
        name = name,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun categoryState() = TxnEditUiState(
        isLoading = false,
        ledgerId = LEDGER_ID,
        type = TransactionType.EXPENSE,
        categoryNodes = listOf(
            CategoryNode(
                category = category("food", "餐饮"),
                children = listOf(category("breakfast", "早餐", parentId = "food")),
            ),
            CategoryNode(category = category("travel", "交通")),
            CategoryNode(category = category("salary", "工资", kind = CategoryKind.INCOME)),
        ),
    )

    private fun category(
        id: String,
        name: String,
        parentId: String? = null,
        kind: CategoryKind = CategoryKind.EXPENSE,
    ) = Category(
        id = id,
        ledgerId = LEDGER_ID,
        parentId = parentId,
        name = name,
        kind = kind,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        const val LEDGER_ID = "l1"
    }
}
