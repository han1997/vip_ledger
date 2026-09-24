package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class TxnValidatorTest {

    private val cash = Account(
        id = "cash",
        ledgerId = "l1",
        name = "现金",
        type = AccountType.CASH,
        createdAt = 0,
        updatedAt = 0,
    )
    private val card = Account(
        id = "card",
        ledgerId = "l1",
        name = "信用卡",
        type = AccountType.CREDIT_CARD,
        createdAt = 0,
        updatedAt = 0,
    )
    private val otherLedgerAccount = cash.copy(id = "foreign", ledgerId = "l2")
    private val accounts = listOf(cash, card, otherLedgerAccount)

    private val categories = listOf(
        Category(id = "food", ledgerId = "l1", name = "餐饮", kind = CategoryKind.EXPENSE, createdAt = 0, updatedAt = 0),
        Category(id = "salary", ledgerId = "l1", name = "工资", kind = CategoryKind.INCOME, createdAt = 0, updatedAt = 0),
        Category(id = "move", ledgerId = "l1", name = "内部转账", kind = CategoryKind.TRANSFER, createdAt = 0, updatedAt = 0),
    )

    private fun txn(
        type: TransactionType = TransactionType.EXPENSE,
        amountCents: Long = 1_000,
        accountId: String = "cash",
        toAccountId: String? = null,
        categoryId: String? = null,
        ledgerId: String = "l1",
        deletedAt: Long? = null,
    ) = Txn(
        id = "t1",
        ledgerId = ledgerId,
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        occurredAt = 0,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = deletedAt,
    )

    @Test
    fun acceptsPlainExpenseWithoutCategory() {
        assertNull(TxnValidator.validate(txn(categoryId = "food"), accounts, categories))
        assertNull(TxnValidator.validate(txn(categoryId = null), accounts, categories))
    }

    @Test
    fun rejectsNonPositiveAmountsExceptForAdjustments() {
        assertIs<AppError.Validation>(TxnValidator.validate(txn(amountCents = 0), accounts, categories))
        assertIs<AppError.Validation>(TxnValidator.validate(txn(amountCents = -100), accounts, categories))
        assertIs<AppError.Validation>(TxnValidator.validate(txn(type = TransactionType.ADJUST, amountCents = 0), accounts, categories))
        assertNull(TxnValidator.validate(txn(type = TransactionType.ADJUST, amountCents = -100), accounts, categories))
    }

    @Test
    fun rejectsUnknownOrForeignAccounts() {
        assertIs<AppError.Validation>(TxnValidator.validate(txn(accountId = "nope"), accounts, categories))
        assertIs<AppError.Validation>(TxnValidator.validate(txn(accountId = "foreign"), accounts, categories))
        assertIs<AppError.Validation>(
            TxnValidator.validate(txn(), listOf(cash.copy(deletedAt = 1), card), categories),
        )
    }

    @Test
    fun transferNeedsADifferentTargetAccountInTheSameLedger() {
        assertIs<AppError.Validation>(
            TxnValidator.validate(txn(type = TransactionType.TRANSFER, categoryId = "move"), accounts, categories),
        )
        assertIs<AppError.Validation>(
            TxnValidator.validate(txn(type = TransactionType.TRANSFER, toAccountId = "cash"), accounts, categories),
        )
        assertIs<AppError.Validation>(
            TxnValidator.validate(txn(type = TransactionType.TRANSFER, toAccountId = "foreign"), accounts, categories),
        )
        assertNull(TxnValidator.validate(txn(type = TransactionType.TRANSFER, toAccountId = "card"), accounts, categories))
    }

    @Test
    fun repaymentTargetMustBeADebtAccount() {
        assertIs<AppError.Validation>(
            TxnValidator.validate(txn(type = TransactionType.DEBT_REPAY, toAccountId = "cash"), accounts, categories),
        )
        assertNull(TxnValidator.validate(txn(type = TransactionType.DEBT_REPAY, toAccountId = "card"), accounts, categories))
    }

    @Test
    fun categoryMustMatchTheTxnDirection() {
        assertIs<AppError.Validation>(TxnValidator.validate(txn(categoryId = "salary"), accounts, categories))
        assertIs<AppError.Validation>(TxnValidator.validate(txn(categoryId = "move"), accounts, categories))
        assertNull(TxnValidator.validate(txn(type = TransactionType.INCOME, categoryId = "salary"), accounts, categories))
        assertNull(TxnValidator.validate(txn(type = TransactionType.TRANSFER, toAccountId = "card", categoryId = "move"), accounts, categories))
    }
}