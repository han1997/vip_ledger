package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.DeleteTxnUseCase
import com.han1997.vipledger.core.domain.usecase.txn.UpdateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TxnUseCasesTest {

    private val cash = Account(
        id = "cash",
        ledgerId = "l1",
        name = "现金",
        type = AccountType.CASH,
        initialBalance = Money.ofCents(100_000),
        currentBalance = Money.ofCents(100_000),
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
    private val food = Category(id = "food", ledgerId = "l1", name = "餐饮", kind = CategoryKind.EXPENSE, createdAt = 0, updatedAt = 0)

    private val txnRepository = FakeTxnRepository()
    private val accountRepository = FakeAccountRepository(mutableListOf(cash, card))
    private val categoryRepository = FakeCategoryRepository(mutableListOf(food))
    private val runner = FakeTransactionRunner()
    private val syncer = RecordingBalanceSyncer()
    private val timeProvider = FakeTimeProvider(now = 1_767_225_600_000L)
    private val idGenerator = SequentialIdGenerator()

    private val create = CreateTxnUseCase(
        txnRepository = txnRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRunner = runner,
        balanceSyncer = syncer,
        timeProvider = timeProvider,
        idGenerator = idGenerator,
    )
    private val update = UpdateTxnUseCase(
        txnRepository = txnRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRunner = runner,
        balanceSyncer = syncer,
        timeProvider = timeProvider,
    )
    private val delete = DeleteTxnUseCase(
        txnRepository = txnRepository,
        transactionRunner = runner,
        balanceSyncer = syncer,
        timeProvider = timeProvider,
    )

    private fun draft(
        type: TransactionType = TransactionType.EXPENSE,
        amountCents: Long = 3_000,
        accountId: String = "cash",
        toAccountId: String? = null,
        categoryId: String? = "food",
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
    ) = TxnDraft(
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        draftStatus = draftStatus,
    )

    @Test
    fun createWritesTxnInOneTransactionAndRefreshesBalances() = runTest {
        val result = create(draft())
        val saved = assertIs<AppResult.Success<Txn>>(result).value
        assertEquals(1, txnRepository.snapshot().size)
        assertEquals(timeProvider.now, saved.occurredAt)
        assertEquals("id-1", saved.id)
        assertEquals(listOf("cash"), syncer.syncedIds)
        assertEquals(1, runner.transactionCount)
    }

    @Test
    fun createRejectsInvalidDraftsWithoutTouchingTheDatabase() = runTest {
        val noTarget = create(draft(type = TransactionType.TRANSFER, categoryId = null))
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(noTarget).error)

        val zeroAmount = create(draft(amountCents = 0))
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(zeroAmount).error)

        val wrongKind = create(draft(type = TransactionType.INCOME))
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(wrongKind).error)

        assertTrue(txnRepository.snapshot().isEmpty())
        assertEquals(0, syncer.calls.size)
        assertEquals(0, runner.transactionCount)
    }

    @Test
    fun createKeepsPendingDraftsOutOfBalances() = runTest {
        val result = create(draft(draftStatus = DraftStatus.PENDING))
        assertIs<AppResult.Success<Txn>>(result)
        assertEquals(DraftStatus.PENDING, txnRepository.snapshot().single().draftStatus)
    }

    @Test
    fun updateRefreshesBalancesForBothOldAndNewAccounts() = runTest {
        val created = assertIs<AppResult.Success<Txn>>(create(draft())).value
        syncer.calls.clear()
        val moved = created.copy(accountId = "card")
        assertIs<AppResult.Success<Txn>>(update(moved))
        assertEquals(setOf("card", "cash"), syncer.syncedIds.toSet())
        assertEquals("card", txnRepository.snapshot().single().accountId)
    }

    @Test
    fun updateAndDeleteReportMissingTxns() = runTest {
        val missing = Txn(
            id = "nope",
            ledgerId = "l1",
            type = TransactionType.EXPENSE,
            amount = Money.ofCents(100),
            accountId = "cash",
            occurredAt = 0,
            createdAt = 0,
            updatedAt = 0,
        )
        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(update(missing)).error)
        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(delete("nope")).error)
    }

    @Test
    fun deleteSoftDeletesAndRestoreBringsItBack() = runTest {
        val created = assertIs<AppResult.Success<Txn>>(create(draft())).value
        syncer.calls.clear()
        assertIs<AppResult.Success<Unit>>(delete(created.id))
        assertEquals(timeProvider.now, txnRepository.snapshot().single().deletedAt)
        assertEquals(listOf("cash"), syncer.syncedIds)

        assertIs<AppResult.Success<Unit>>(delete.restore(created.id))
        assertEquals(null, txnRepository.snapshot().single().deletedAt)
    }
}