package com.han1997.vipledger.feature.ledger.ui.txnedit

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.AccountType
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

    private companion object {
        const val LEDGER_ID = "l1"
    }
}
