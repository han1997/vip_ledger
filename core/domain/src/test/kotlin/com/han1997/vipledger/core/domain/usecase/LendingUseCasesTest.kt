package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.getOrThrow
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeLendingRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.LendingCalculator
import com.han1997.vipledger.core.domain.model.LendingProgress
import com.han1997.vipledger.core.domain.model.LendingRecord
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.usecase.lending.CloseLendingUseCase
import com.han1997.vipledger.core.domain.usecase.lending.CreateLendingUseCase
import com.han1997.vipledger.core.domain.usecase.lending.LendingTxnGuard
import com.han1997.vipledger.core.domain.usecase.lending.RecordLendingInterestUseCase
import com.han1997.vipledger.core.domain.usecase.lending.SettleLendingUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.LendingLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class LendingUseCasesTest {
    private val zone = TimeZone.of("Asia/Shanghai")
    private val october = LocalDate(2026, 10, 1).startOfDayEpochMillis(zone)
    private val time = FakeTimeProvider(now = LocalDate(2026, 10, 5).startOfDayEpochMillis(zone))
    private val cash = Account(
        id = "cash",
        ledgerId = "l1",
        name = "现金",
        type = AccountType.CASH,
        initialBalance = Money.ofCents(500_000),
        currentBalance = Money.ofCents(500_000),
        createdAt = 0,
        updatedAt = 0,
    )

    private val lending = FakeLendingRepository()
    private val txnRepo = FakeTxnRepository()
    private val accountRepo = FakeAccountRepository(mutableListOf(cash))
    private val categoryRepo = FakeCategoryRepository()
    private val runner = FakeTransactionRunner()
    private val syncer = RecordingBalanceSyncer()
    private val ids = SequentialIdGenerator()

    private val create = CreateTxnUseCase(txnRepo, accountRepo, categoryRepo, runner, syncer, time, ids)
    private val createLending = CreateLendingUseCase(lending, accountRepo, create, runner, time, ids)
    private val settle = SettleLendingUseCase(lending, txnRepo, accountRepo, create, runner, time)
    private val interest = RecordLendingInterestUseCase(lending, accountRepo, create, runner, time)
    private val close = CloseLendingUseCase(lending, runner, time)
    private val guard = LendingTxnGuard(lending, txnRepo)

    private suspend fun newLending(
        direction: LendingDirection = LendingDirection.LEND,
        principal: Long = 100_000,
        counterparty: String = "张三",
        due: LocalDate? = null,
    ): LendingRecord = createLending(
        ledgerId = "l1",
        direction = direction,
        counterparty = counterparty,
        principal = Money.ofCents(principal),
        cashAccountId = "cash",
        dueDate = due,
        remark = "",
        occurredAt = october,
        title = "本金流转",
    ).getOrThrow()

    private fun txn(id: String): Txn = txnRepo.snapshot().first { it.id == id }

    @Test
    fun lendPostsTransferFromCashToReceivableAndBorrowToPayable() = runTest {
        val lend = newLending()
        val receivable = accountRepo.snapshot().first { it.id == lend.accountId }
        assertEquals(AccountType.RECEIVABLE, receivable.type)
        assertEquals("张三", receivable.name)
        assertEquals("l1", receivable.ledgerId)
        val disburse = txnRepo.snapshot().single()
        assertEquals(TransactionType.TRANSFER, disburse.type)
        assertEquals("cash", disburse.accountId)
        assertEquals(lend.accountId, disburse.toAccountId)
        assertNull(disburse.categoryId)
        assertEquals(LendingLinkKind.DISBURSE, lending.links(lend.id).single().kind)

        val borrow = newLending(direction = LendingDirection.BORROW, principal = 50_000, counterparty = "李四")
        val payable = accountRepo.snapshot().first { it.id == borrow.accountId }
        assertEquals(AccountType.PAYABLE, payable.type)
        val borrowed = txnRepo.snapshot().first { it.id == lending.links(borrow.id).single().txnId }
        assertEquals(borrow.accountId, borrowed.accountId)
        assertEquals("cash", borrowed.toAccountId)
    }

    @Test
    fun accountNameGetsSuffixWhenTheCounterpartyNameIsTaken() = runTest {
        accountRepo.upsert(cash.copy(id = "other", name = "张三"))
        val lend = newLending()
        assertEquals("张三-2", accountRepo.snapshot().first { it.id == lend.accountId }.name)
    }

    @Test
    fun partialSettleTracksOutstandingAndSettlesWhenItReachesZero() = runTest {
        val lend = newLending()
        val first = assertIs<AppResult.Success<Txn>>(
            settle(lend.id, "cash", Money.ofCents(40_000), october, "归还部分"),
        ).value
        assertEquals(lend.accountId, first.accountId)
        assertEquals("cash", first.toAccountId)
        val record = lending.snapshots().first.single()
        assertEquals(Money.ofCents(60_000), LendingCalculator.outstanding(record, lending.links(lend.id), txnRepo.snapshot()))
        assertEquals(LendingLinkKind.SETTLE, lending.links(lend.id).last().kind)

        assertTrue(settle(lend.id, "cash", Money.ofCents(60_000), october, "结清").isSuccess)
        val progressAfter = LendingProgress(
            lending.snapshots().first.single(), lending.links(lend.id), txnRepo.snapshot(),
        )
        assertEquals(Money.ZERO, progressAfter.outstanding)
        assertTrue(progressAfter.settled)
        assertFalse(progressAfter.active)
        assertFalse(settle(lend.id, "cash", Money.ofCents(1), october, "多余").isSuccess)
    }

    @Test
    fun settleRejectsOverPaymentZeroAndClosedRecords() = runTest {
        val lend = newLending()
        val before = txnRepo.snapshot().size
        assertFalse(settle(lend.id, "cash", Money.ofCents(120_000), october, "超额").isSuccess)
        assertFalse(settle(lend.id, "cash", Money.ZERO, october, "零").isSuccess)
        assertEquals(before, txnRepo.snapshot().size)
        assertTrue(close(lend.id, true).isSuccess)
        assertFalse(settle(lend.id, "cash", Money.ofCents(10_000), october, "已终止").isSuccess)
    }

    @Test
    fun interestPostsIncomeForLendAndExpenseForBorrowWithoutTouchingPrincipal() = runTest {
        val lend = newLending()
        val earned = assertIs<AppResult.Success<Txn>>(
            interest(lend.id, "cash", Money.ofCents(5_000), october, "收到利息"),
        ).value
        assertEquals(TransactionType.INCOME, earned.type)
        val lendProgress = LendingProgress(
            lending.snapshots().first.single(), lending.links(lend.id), txnRepo.snapshot(),
        )
        assertEquals(Money.ofCents(5_000), lendProgress.interestTotal)
        assertEquals(Money.ofCents(100_000), lendProgress.outstanding)

        val borrow = newLending(direction = LendingDirection.BORROW, counterparty = "李四")
        val paid = assertIs<AppResult.Success<Txn>>(
            interest(borrow.id, "cash", Money.ofCents(3_000), october, "支付利息"),
        ).value
        assertEquals(TransactionType.EXPENSE, paid.type)
    }

    @Test
    fun interestAndSettleAreRejectedWhileClosed() = runTest {
        val lend = newLending()
        assertTrue(close(lend.id, true).isSuccess)
        assertFalse(interest(lend.id, "cash", Money.ofCents(1_000), october, "利息").isSuccess)
        var progress = LendingProgress(
            lending.snapshots().first.single(), lending.links(lend.id), txnRepo.snapshot(),
        )
        assertFalse(progress.active)
        assertTrue(close(lend.id, false).isSuccess)
        progress = LendingProgress(
            lending.snapshots().first.single(), lending.links(lend.id), txnRepo.snapshot(),
        )
        assertTrue(progress.active)
    }

    @Test
    fun failedCreateLeavesNoAccountRecordOrTxn() = runTest {
        accountRepo.upsert(cash.copy(id = "card", name = "信用卡", type = AccountType.CREDIT_CARD))
        val accountsBefore = accountRepo.snapshot().size
        assertFalse(
            createLending("l1", LendingDirection.LEND, "张三", Money.ofCents(100_000), "card", null, "", october, "借出").isSuccess,
        )
        assertEquals(accountsBefore, accountRepo.snapshot().size)
        assertTrue(lending.snapshots().first.isEmpty())
        assertTrue(txnRepo.snapshot().isEmpty())
    }

    @Test
    fun guardLocksPrincipalAndValidatesDirectionsAndOverSettle() = runTest {
        val lend = newLending()
        val disburse = txn(lending.links(lend.id).single().txnId)
        val settleTxn = assertIs<AppResult.Success<Txn>>(
            settle(lend.id, "cash", Money.ofCents(40_000), october, "归还部分"),
        ).value
        val interestTxn = assertIs<AppResult.Success<Txn>>(
            interest(lend.id, "cash", Money.ofCents(1_000), october, "利息"),
        ).value

        assertNull(guard.validate(disburse))
        assertEquals(LendingTxnGuard.PRINCIPAL_LOCKED, guard.validate(disburse.copy(amount = Money.ofCents(50_000)))?.message)
        assertEquals(LendingTxnGuard.PRINCIPAL_LOCKED, guard.validate(disburse.copy(deletedAt = 999))?.message)
        assertEquals(LendingTxnGuard.DIRECTION, guard.validate(disburse.copy(type = TransactionType.EXPENSE))?.message)
        assertEquals(LendingTxnGuard.DIRECTION, guard.validate(disburse.copy(toAccountId = "cash"))?.message)

        assertNull(guard.validate(settleTxn))
        assertEquals(LendingTxnGuard.DIRECTION, guard.validate(settleTxn.copy(accountId = "cash"))?.message)
        assertEquals(LendingTxnGuard.OVER_SETTLE, guard.validate(settleTxn.copy(amount = Money.ofCents(110_000)))?.message)

        assertNull(guard.validate(interestTxn))
        assertEquals(LendingTxnGuard.DIRECTION, guard.validate(interestTxn.copy(type = TransactionType.EXPENSE))?.message)

        assertNull(guard.validate(disburse.copy(id = "unrelated")))
    }

    @Test
    fun borrowSettleDirectionFlowsIntoThePayableAccount() = runTest {
        val borrow = newLending(direction = LendingDirection.BORROW, counterparty = "李四")
        val settleTxn = assertIs<AppResult.Success<Txn>>(
            settle(borrow.id, "cash", Money.ofCents(30_000), october, "归还借款"),
        ).value
        assertEquals("cash", settleTxn.accountId)
        assertEquals(borrow.accountId, settleTxn.toAccountId)
        assertNull(guard.validate(settleTxn))
        assertEquals(LendingTxnGuard.DIRECTION, guard.validate(settleTxn.copy(toAccountId = "cash"))?.message)
    }
}
