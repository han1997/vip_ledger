package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.fake.FakeInstallmentRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.model.InstallmentLink
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.loan.InstallmentTxnGuard
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPostingKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/** 分期还款流水的改/删守卫：类型方向与目标账户锁定，累计不得超过当期应还。 */
class InstallmentTxnGuardTest {

    private val plan = InstallmentPlan(
        id = "plan",
        ledgerId = "l1",
        accountId = "card",
        name = "花呗",
        method = InstallmentMethod.EQUAL_PRINCIPAL,
        principal = Money.ofCents(100_000),
        periods = 1,
        firstDueDate = LocalDate(2026, 1, 15),
        createdAt = 0,
        updatedAt = 0,
    )

    private val period = InstallmentPeriod(
        id = "p1",
        planId = "plan",
        periodNo = 1,
        dueDate = LocalDate(2026, 1, 15),
        principal = Money.ofCents(100_000),
        interest = Money.ofCents(1_000),
        fee = Money.ofCents(500),
    )

    private fun txn(
        id: String,
        type: TransactionType,
        amount: Long,
        toAccountId: String? = null,
        deletedAt: Long? = null,
    ) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amount),
        accountId = "cash",
        toAccountId = toAccountId,
        occurredAt = 1,
        title = id,
        draftStatus = DraftStatus.CONFIRMED,
        createdAt = 1,
        updatedAt = 1,
        deletedAt = deletedAt,
    )

    private class Fixture(
        plan: InstallmentPlan,
        period: InstallmentPeriod,
        txns: List<Txn>,
        links: List<InstallmentLink>,
    ) {
        val installments = FakeInstallmentRepository(mutableListOf(plan), mutableListOf(period), links.toMutableList())
        val txnRepo = FakeTxnRepository(txns.toMutableList())
        val guard = InstallmentTxnGuard(installments, txnRepo, FakeTimeProvider())
    }

    private fun fixture(
        txns: List<Txn> = listOf(
            txn("t-principal", TransactionType.TRANSFER, 100_000, toAccountId = "card"),
            txn("t-interest", TransactionType.EXPENSE, 1_000),
        ),
        links: List<InstallmentLink> = listOf(
            InstallmentLink("t-principal", "plan", 1, InstallmentPostingKind.PRINCIPAL, 1),
            InstallmentLink("t-interest", "plan", 1, InstallmentPostingKind.INTEREST, 1),
        ),
    ) = Fixture(plan, period, txns, links)

    @Test
    fun unlinkedTransactionsPassThrough() = runTest {
        val fixture = fixture()
        assertNull(fixture.guard.validate(txn("other", TransactionType.EXPENSE, 100)))
    }

    @Test
    fun principalFlowMustStayATransferIntoTheDebtAccount() = runTest {
        val fixture = fixture()
        val asExpense = txn("t-principal", TransactionType.EXPENSE, 100_000)
        assertIs<AppError.Validation>(assertNotNull(fixture.guard.validate(asExpense)))
        assertEquals(InstallmentTxnGuard.DIRECTION, (fixture.guard.validate(asExpense) as AppError.Validation).message)

        val wrongTarget = txn("t-principal", TransactionType.TRANSFER, 100_000, toAccountId = "savings")
        assertEquals(
            InstallmentTxnGuard.DIRECTION,
            (assertNotNull(fixture.guard.validate(wrongTarget)) as AppError.Validation).message,
        )
    }

    @Test
    fun interestFlowMustStayAnExpenseWithAPositiveAmount() = runTest {
        val fixture = fixture()
        val asTransfer = txn("t-interest", TransactionType.TRANSFER, 1_000)
        assertEquals(
            InstallmentTxnGuard.DIRECTION,
            (assertNotNull(fixture.guard.validate(asTransfer)) as AppError.Validation).message,
        )
        val zero = txn("t-interest", TransactionType.EXPENSE, 0)
        assertEquals(
            InstallmentTxnGuard.AMOUNT_LOCKED,
            (assertNotNull(fixture.guard.validate(zero)) as AppError.Validation).message,
        )
    }

    @Test
    fun raisingAnAmountAboveThePeriodOutstandingIsRejected() = runTest {
        val fixture = fixture()
        val tooMuch = txn("t-interest", TransactionType.EXPENSE, 2_000)
        assertEquals(
            InstallmentTxnGuard.OVER_PAID,
            (assertNotNull(fixture.guard.validate(tooMuch)) as AppError.Validation).message,
        )
    }

    @Test
    fun deletingAnInterestTxnResyncsThePeriodBackToPending() = runTest {
        val fixture = fixture()
        val deleted = txn("t-interest", TransactionType.EXPENSE, 1_000, deletedAt = 5)
        assertNull(fixture.guard.validate(deleted))

        val stored = assertNotNull(fixture.installments.getPeriod("plan", 1))
        assertEquals(InstallmentPeriodStatus.PARTIAL, stored.status)
        assertEquals(Money.ofCents(100_000), stored.paidPrincipal)
        assertEquals(Money.ZERO, stored.paidInterest)
    }

    @Test
    fun prepayAmountsAreLockedAndCannotBeDeleted() = runTest {
        val prepayTxn = txn("t-prepay", TransactionType.TRANSFER, 50_000, toAccountId = "card")
        val fixture = fixture(
            txns = listOf(prepayTxn),
            links = listOf(InstallmentLink("t-prepay", "plan", 1, InstallmentPostingKind.PREPAY, 1)),
        )
        assertNull(fixture.guard.validate(prepayTxn))
        assertEquals(
            InstallmentTxnGuard.AMOUNT_LOCKED,
            fixture.guard.validate(prepayTxn.copy(amount = Money.ofCents(60_000))).validationMessage(),
        )
        assertEquals(
            InstallmentTxnGuard.AMOUNT_LOCKED,
            (assertNotNull(fixture.guard.validate(prepayTxn.copy(deletedAt = 2))) as AppError.Validation).message,
        )
    }

    @Test
    fun danglingLinksAreRejected() = runTest {
        val fixture = fixture(links = listOf(InstallmentLink("t-interest", "gone", 1, InstallmentPostingKind.INTEREST, 1)))
        assertEquals(
            InstallmentTxnGuard.LINK_INVALID,
            fixture.guard.validate(txn("t-interest", TransactionType.EXPENSE, 1_000)).validationMessage(),
        )
    }

    private fun AppError?.validationMessage(): String? = (this as? AppError.Validation)?.message
}
