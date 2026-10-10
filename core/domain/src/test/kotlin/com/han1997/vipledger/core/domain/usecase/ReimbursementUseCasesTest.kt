package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.getOrThrow
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.calculator.StatsCalculator
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeReimbursementRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.model.TxnDraft
import com.han1997.vipledger.core.domain.usecase.reimburse.CloseReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ObserveReimbursementsUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ReceiveReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.reimburse.ReimbursementTxnGuard
import com.han1997.vipledger.core.domain.usecase.reimburse.SaveReimbursementUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.DateRange
import com.han1997.vipledger.core.model.time.startOfDayEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ReimbursementUseCasesTest {
    private val zone = TimeZone.of("Asia/Shanghai")
    private val september = LocalDate(2026, 9, 1).startOfDayEpochMillis(zone)
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
    private val food = Category(id = "food", ledgerId = "l1", name = "差旅", kind = CategoryKind.EXPENSE, createdAt = 0, updatedAt = 0)

    private val reimburse = FakeReimbursementRepository()
    private val txnRepo = FakeTxnRepository()
    private val accountRepo = FakeAccountRepository(mutableListOf(cash))
    private val categoryRepo = FakeCategoryRepository(mutableListOf(food))
    private val runner = FakeTransactionRunner()
    private val syncer = RecordingBalanceSyncer()
    private val ids = SequentialIdGenerator()

    private val create = CreateTxnUseCase(txnRepo, accountRepo, categoryRepo, runner, syncer, time, ids)
    private val observe = ObserveReimbursementsUseCase(reimburse, txnRepo)
    private val save = SaveReimbursementUseCase(reimburse, txnRepo, runner, time, ids)
    private val receive = ReceiveReimbursementUseCase(reimburse, txnRepo, accountRepo, create, runner)
    private val close = CloseReimbursementUseCase(reimburse, runner, time)
    private val guard = ReimbursementTxnGuard(reimburse, txnRepo)

    private suspend fun seedExpense(cents: Long, at: Long = september, excludeFromStats: Boolean = false): Txn = create(
        TxnDraft(
            ledgerId = "l1",
            type = TransactionType.EXPENSE,
            amount = Money.ofCents(cents),
            accountId = "cash",
            categoryId = "food",
            occurredAt = at,
            title = "出差机票",
            excludeFromStats = excludeFromStats,
        ),
    ).getOrThrow()

    private suspend fun newProject(vararg itemIds: String): String {
        assertTrue(save(null, "l1", "出差报销", "公司", "备注", itemIds.toList()).isSuccess)
        return reimburse.snapshots().first.single().id
    }

    @Test
    fun partialThenFullReceiptSettlesAndInheritsOriginalCategoryAndStats() = runTest {
        val original = seedExpense(100_000, excludeFromStats = true)
        val projectId = newProject(original.id)

        val receipt = assertIs<AppResult.Success<Txn>>(
            receive(projectId, original.id, Money.ofCents(60_000), "cash", october, "报销到账"),
        ).value
        assertEquals(TransactionType.REFUND, receipt.type)
        assertEquals(original.id, receipt.refundOfTxnId)
        assertEquals("food", receipt.categoryId)
        assertTrue(receipt.excludeFromStats)

        var progress = observe("l1").first().single()
        assertEquals(Money.ofCents(100_000), progress.claimable)
        assertEquals(Money.ofCents(60_000), progress.received)
        assertEquals(Money.ofCents(40_000), progress.pending)
        assertFalse(progress.settled)

        assertTrue(receive(projectId, original.id, Money.ofCents(40_000), "cash", october, "尾款").isSuccess)
        progress = observe("l1").first().single()
        assertTrue(progress.settled)
        assertEquals(Money.ZERO, progress.pending)
    }

    @Test
    fun receiptReducesItsMonthExpenseWhileClaimStaysVisible() = runTest {
        val original = seedExpense(100_000)
        val projectId = newProject(original.id)
        receive(projectId, original.id, Money.ofCents(60_000), "cash", october, "报销到账").getOrThrow()

        val txns = txnRepo.snapshot()
        val septemberSummary = StatsCalculator.summarize(
            txns, listOf(food), emptyList(), DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), zone,
        )
        val octoberSummary = StatsCalculator.summarize(
            txns, listOf(food), emptyList(), DateRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31)), zone,
        )
        assertEquals(Money.ofCents(100_000), septemberSummary.expense)
        assertEquals(Money.ofCents(-60_000), octoberSummary.expense)
        assertEquals(Money.ofCents(40_000), observe("l1").first().single().pending)
    }

    @Test
    fun duplicateOverAndZeroReceiptsAreRejectedWithoutWriting() = runTest {
        val original = seedExpense(100_000)
        val projectId = newProject(original.id)
        assertTrue(receive(projectId, original.id, Money.ofCents(60_000), "cash", october, "第一笔").isSuccess)

        val before = txnRepo.snapshot().size
        assertFalse(receive(projectId, original.id, Money.ofCents(60_000), "cash", october, "重复").isSuccess)
        assertFalse(receive(projectId, original.id, Money.ofCents(50_000), "cash", october, "超额").isSuccess)
        assertFalse(receive(projectId, original.id, Money.ZERO, "cash", october, "零").isSuccess)
        assertEquals(before, txnRepo.snapshot().size)
        assertEquals(Money.ofCents(40_000), observe("l1").first().single().pending)
    }

    @Test
    fun plainRefundAndReimbursementShareTheOriginalExpenseCap() = runTest {
        val original = seedExpense(100_000)
        val projectId = newProject(original.id)
        assertTrue(
            create(
                TxnDraft(
                    ledgerId = "l1",
                    type = TransactionType.REFUND,
                    amount = Money.ofCents(70_000),
                    accountId = "cash",
                    categoryId = "food",
                    occurredAt = october,
                    refundOfTxnId = original.id,
                ),
            ).isSuccess,
        )
        assertFalse(receive(projectId, original.id, Money.ofCents(40_000), "cash", october, "超出共用上限").isSuccess)
        assertTrue(receive(projectId, original.id, Money.ofCents(30_000), "cash", october, "正好用完").isSuccess)
    }

    @Test
    fun saveRejectsDraftMissingIncomeAndSharedItems() = runTest {
        val expense = seedExpense(100_000)
        val draft = create(
            TxnDraft(
                ledgerId = "l1",
                type = TransactionType.EXPENSE,
                amount = Money.ofCents(5_000),
                accountId = "cash",
                categoryId = "food",
                draftStatus = DraftStatus.PENDING,
            ),
        ).getOrThrow()

        assertFalse(save(null, "l1", "P", "", "", listOf(draft.id)).isSuccess)
        assertFalse(save(null, "l1", "P", "", "", listOf("missing")).isSuccess)
        assertFalse(save(null, "l1", "", "", "", listOf(expense.id)).isSuccess)
        newProject(expense.id)
        assertFalse(save(null, "l1", "第二项目", "", "", listOf(expense.id)).isSuccess)
        assertEquals(1, reimburse.snapshots().second.size)
    }

    @Test
    fun closedProjectRejectsReceiptsButKeepsExistingOnes() = runTest {
        val original = seedExpense(100_000)
        val projectId = newProject(original.id)
        receive(projectId, original.id, Money.ofCents(30_000), "cash", october, "到账").getOrThrow()

        assertTrue(close(projectId, true).isSuccess)
        assertFalse(receive(projectId, original.id, Money.ofCents(10_000), "cash", october, "终止后").isSuccess)
        assertEquals(Money.ofCents(30_000), observe("l1").first().single().received)

        assertTrue(close(projectId, false).isSuccess)
        assertTrue(receive(projectId, original.id, Money.ofCents(10_000), "cash", october, "恢复后").isSuccess)
    }

    @Test
    fun saveBlocksRemovingItemsThatAlreadyHaveReceipts() = runTest {
        val first = seedExpense(100_000)
        val second = seedExpense(50_000)
        val projectId = newProject(first.id, second.id)
        receive(projectId, first.id, Money.ofCents(20_000), "cash", october, "到账").getOrThrow()

        assertFalse(save(projectId, "l1", "改名", "", "", listOf(second.id)).isSuccess)
        assertTrue(save(projectId, "l1", "改名", "", "", listOf(first.id, second.id)).isSuccess)
        assertEquals(setOf(first.id, second.id), reimburse.snapshots().second.map { it.txnId }.toSet())
    }

    @Test
    fun saveLinksEveryItemInOneTransaction() = runTest {
        val first = seedExpense(100_000)
        val second = seedExpense(50_000)
        val before = runner.transactionCount
        newProject(first.id, second.id)
        assertEquals(before + 1, runner.transactionCount)
        assertEquals(setOf(first.id, second.id), reimburse.snapshots().second.map { it.txnId }.toSet())
    }

    @Test
    fun guardLocksItemsAndReceiptShape() = runTest {
        val original = seedExpense(100_000)
        val projectId = newProject(original.id)
        val receipt = assertIs<AppResult.Success<Txn>>(
            receive(projectId, original.id, Money.ofCents(20_000), "cash", october, "到账"),
        ).value

        assertNull(guard.validate(original))
        assertEquals(ReimbursementTxnGuard.ITEM_LOCKED, guard.validate(original.copy(deletedAt = 999))?.message)
        assertEquals(ReimbursementTxnGuard.ITEM_LOCKED, guard.validate(original.copy(type = TransactionType.INCOME))?.message)
        assertEquals(ReimbursementTxnGuard.ITEM_LOCKED, guard.validate(original.copy(draftStatus = DraftStatus.PENDING))?.message)
        assertNull(guard.validate(receipt.copy(deletedAt = 999)))
        assertEquals(ReimbursementTxnGuard.RECEIPT_LOCKED, guard.validate(receipt.copy(type = TransactionType.EXPENSE))?.message)
        assertEquals(ReimbursementTxnGuard.RECEIPT_LOCKED, guard.validate(receipt.copy(refundOfTxnId = null))?.message)
        assertNull(guard.validate(original.copy(id = "unrelated")))
    }
}
