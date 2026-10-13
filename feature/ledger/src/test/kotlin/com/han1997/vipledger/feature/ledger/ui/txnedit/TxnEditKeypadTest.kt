package com.han1997.vipledger.feature.ledger.ui.txnedit

import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 记一笔编辑页的纯函数规则（原型 v2.1）：
 * 数字键盘输入约束与「保存可用」判定写在 UiState 文件外边、可单测,避免靠手工走查。
 */
class TxnEditKeypadTest {

    // ── keypadAppend ─────────────────────────────────────────

    @Test
    fun digitsAppend() {
        assertEquals("12", keypadAppend("1", "2"))
    }

    @Test
    fun deleteRemovesLastCharacter() {
        assertEquals("12", keypadAppend("123", "⌫"))
        assertEquals("", keypadAppend("1", "⌫"))
    }

    @Test
    fun onlyOneDecimalPointIsAccepted() {
        assertEquals("1.", keypadAppend("1", "."))
        assertEquals("1.23", keypadAppend("1.2", "3"))
        assertEquals("1.2", keypadAppend("1.2", "."))
    }

    @Test
    fun atMostTwoDecimalPlaces() {
        assertEquals("1.23", keypadAppend("1.23", "4"))
        assertEquals("1.23", keypadAppend("1.23", "5"))
    }

    @Test
    fun totalLengthIsCapped() {
        val twelve = "123456789012"
        assertEquals(twelve, keypadAppend(twelve, "3"))
    }

    @Test
    fun decimalPointAloneIsNotAValidAmount() {
        // 键盘允许输入 ".",但保存判定（Money.parse）会拦住它。
        assertFalse(saveEnabled(TxnEditUiState(isLoading = false, type = TransactionType.EXPENSE, amountInput = ".")))
    }

    // ── saveEnabled ──────────────────────────────────────────

    private fun state(type: TransactionType, amount: String, busy: Boolean = false, unavailable: Boolean = false) =
        TxnEditUiState(
            isLoading = false,
            type = type,
            amountInput = amount,
            isSaving = busy,
            editingUnavailable = unavailable,
        )

    @Test
    fun emptyAmountCannotBeSaved() {
        assertFalse(saveEnabled(state(TransactionType.EXPENSE, "")))
    }

    @Test
    fun positiveAmountCanBeSaved() {
        assertTrue(saveEnabled(state(TransactionType.EXPENSE, "38.00")))
    }

    @Test
    fun busyOrUnavailableBlocksSave() {
        assertFalse(saveEnabled(state(TransactionType.EXPENSE, "38.00", busy = true)))
        assertFalse(saveEnabled(state(TransactionType.EXPENSE, "38.00", unavailable = true)))
    }

    @Test
    fun adjustAcceptsNonZeroWhileOthersRequirePositive() {
        assertTrue(saveEnabled(state(TransactionType.ADJUST, "0.01")))
        assertFalse(saveEnabled(state(TransactionType.ADJUST, "0")))
        assertFalse(saveEnabled(state(TransactionType.EXPENSE, "0")))
    }
}