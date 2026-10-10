package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.getOrThrow
import com.han1997.vipledger.core.domain.calculator.InstallmentCalculator
import com.han1997.vipledger.core.domain.calculator.InstallmentScheduleInput
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeInstallmentRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.loan.ChangeInstallmentRateInput
import com.han1997.vipledger.core.domain.usecase.loan.ChangeInstallmentRateUseCase
import com.han1997.vipledger.core.domain.usecase.loan.CorrectLegacyInstallmentRepaymentUseCase
import com.han1997.vipledger.core.domain.usecase.loan.PrepayInstallmentPlanInput
import com.han1997.vipledger.core.domain.usecase.loan.PrepayInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.loan.PreviewLegacyInstallmentRepaymentsUseCase
import com.han1997.vipledger.core.domain.usecase.loan.SettleInstallmentPlanInput
import com.han1997.vipledger.core.domain.usecase.loan.SettleInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.InstallmentPostingKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/** 提前结清 / 部分提前还款 / 利率变更 / 旧口径差异预览与修正。 */
class InstallmentRepaymentUseCasesTest {

    private val card = Account("card", "l1", "信用卡", AccountType.CREDIT_CARD, createdAt = 0, updatedAt = 0)
    private val cash = Account("cash", "l1", "现金", AccountType.CASH, createdAt = 0, updatedAt = 0)

    private val plan = InstallmentPlan(
        id = "plan",
        ledgerId = "l1",
        accountId = "card",
        name = "房贷",
        method = InstallmentMethod.EQUAL_PRINCIPAL,
        principal = Money.ofCents(400_000),
        periods = 4,
        annualRateBp = 1200,
        firstDueDate = LocalDate(2026, 1, 15),
        createdAt = 0,
        updatedAt = 0,
    )

    private fun periods(): List<InstallmentPeriod> =
        InstallmentCalculator.schedule(
            plan.method,
            InstallmentScheduleInput(plan.principal, plan.periods, plan.firstDueDate, plan.annualRateBp),
        ).mapIndexed { index, scheduled ->
            InstallmentPeriod(
                id = "p${index + 1}",
                planId = plan.id,
                periodNo = scheduled.periodNo,
                dueDate = scheduled.dueDate,
                principal = scheduled.principal,
                interest = scheduled.interest,
            )
        }

    private fun InstallmentPeriod.markPaid() = copy(
        status = InstallmentPeriodStatus.PAID,
        paidPrincipal = principal,
        paidInterest = interest,
        paidFee = fee,
        paidTxnId = "paid-$periodNo",
        paidAt = 1,
    )

    private class Fixture(
        card: Account,
        cash: Account,
        plans: List<InstallmentPlan>,
        periods: List<InstallmentPeriod>,
        txns: List<Txn>,
    ) {
        val installments = FakeInstallmentRepository(plans.toMutableList(), periods.toMutableList())
        val txnRepo = FakeTxnRepository(txns.toMutableList())
        val accounts = FakeAccountRepository(mutableListOf(card, cash))
        val syncer = RecordingBalanceSyncer()
        val runner = FakeTransactionRunner()
        private val time = FakeTimeProvider()
        private val ids = SequentialIdGenerator("txn")
        private val create = CreateTxnUseCase(
            txnRepo,
            accounts,
            FakeCategoryRepository(),
            runner,
            syncer,
            time,
            ids,
        )

        val settle = SettleInstallmentPlanUseCase(installments, accounts, runner, create, time)
        val prepay = PrepayInstallmentPlanUseCase(installments, accounts, runner, create, time)
        val changeRate = ChangeInstallmentRateUseCase(installments, runner, time)
        val preview = PreviewLegacyInstallmentRepaymentsUseCase(installments, txnRepo)
        val correct = CorrectLegacyInstallmentRepaymentUseCase(installments, txnRepo, runner, syncer, ids, time)
    }

    private fun fixture(
        plans: List<InstallmentPlan> = listOf(plan),
        periods: List<InstallmentPeriod> = periods(),
        txns: List<Txn> = emptyList(),
    ) = Fixture(card, cash, plans, periods, txns)

    @Test
    fun settlePaysEveryOutstandingPeriodAndClosesThePlan() = runTest {
        val fixture = fixture(periods = periods().map { if (it.periodNo == 1) it.markPaid() else it })
        val entries = fixture.settle(SettleInstallmentPlanInput("plan", "cash")).getOrThrow()

        assertEquals(6, entries.size, "第 2–4 期各一条本金转账 + 一条利息支出")
        assertTrue(entries.none { it.installmentPeriodNo == 1 }, "已还期次不再产生流水")
        assertTrue(entries.all { it.installmentPlanId == "plan" })
        assertEquals(3, entries.count { it.type == TransactionType.TRANSFER && it.toAccountId == "card" })
        assertEquals(3, entries.count { it.type == TransactionType.EXPENSE })
        assertTrue(fixture.installments.periodSnapshot().all { it.status == InstallmentPeriodStatus.PAID })
        assertEquals(InstallmentPlanStatus.CLOSED, assertNotNull(fixture.installments.getPlan("plan")).status)
    }

    @Test
    fun settleRejectsClosedOrFullyPaidPlans() = runTest {
        val paid = fixture(periods = periods().map { it.markPaid() })
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(paid.settle(SettleInstallmentPlanInput("plan", "cash"))).error,
        )
        val closed = fixture(plans = listOf(plan.copy(status = InstallmentPlanStatus.CLOSED)))
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(closed.settle(SettleInstallmentPlanInput("plan", "cash"))).error,
        )
    }

    @Test
    fun prepayShrinksPrincipalAndPostsASinglePrepayTransfer() = runTest {
        val fixture = fixture()
        val txn = fixture.prepay(PrepayInstallmentPlanInput("plan", "cash", Money.ofCents(50_000))).getOrThrow()

        assertEquals(TransactionType.TRANSFER, txn.type)
        assertEquals(Money.ofCents(50_000), txn.amount)
        assertEquals("cash", txn.accountId)
        assertEquals("card", txn.toAccountId)
        assertEquals(InstallmentPostingKind.PREPAY, fixture.installments.linkSnapshot().single().kind)

        val stored = assertNotNull(fixture.installments.getPlan("plan"))
        assertEquals(Money.ofCents(350_000), stored.principal)
        assertEquals(Money.ofCents(350_000), Money.sum(fixture.installments.periodSnapshot().map { it.principal }))
        assertEquals(4, fixture.installments.periodSnapshot().size)
    }

    @Test
    fun prepayRejectsAmountsAtOrAboveTheOutstandingPrincipal() = runTest {
        val fixture = fixture()
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                fixture.prepay(PrepayInstallmentPlanInput("plan", "cash", Money.ofCents(400_000))),
            ).error,
        )
    }

    @Test
    fun prepayRejectsPlansWithPartiallyPaidPeriods() = runTest {
        val partial = periods().map { period ->
            if (period.periodNo == 1) {
                period.copy(
                    status = InstallmentPeriodStatus.PARTIAL,
                    paidInterest = period.interest,
                    paidAt = 1,
                    paidTxnId = "partial",
                )
            } else {
                period
            }
        }
        val fixture = fixture(periods = partial)
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                fixture.prepay(PrepayInstallmentPlanInput("plan", "cash", Money.ofCents(10_000))),
            ).error,
        )
    }

    @Test
    fun changeRateOnlyTouchesUntouchedPeriodsAndFreezesHistory() = runTest {
        val fixture = fixture(periods = periods().map { if (it.periodNo == 1) it.markPaid() else it })
        val before = fixture.installments.periodSnapshot()

        fixture.changeRate(ChangeInstallmentRateInput("plan", fromPeriodNo = 3, annualRateBp = 2400)).getOrThrow()

        val after = fixture.installments.periodSnapshot()
        assertEquals(before[1], after[1], "第 2 期在生效期之前，必须保持不变")
        assertTrue(after[2].interest > before[2].interest)
        assertEquals(Money.ofCents(400_000), Money.sum(after.map { it.principal }))
        assertEquals(2400, assertNotNull(fixture.installments.getPlan("plan")).annualRateBp)
    }

    @Test
    fun changeRateRejectsPaidPeriods() = runTest {
        val fixture = fixture(periods = periods().map { if (it.periodNo == 1) it.markPaid() else it })
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                fixture.changeRate(ChangeInstallmentRateInput("plan", fromPeriodNo = 1, annualRateBp = 2400)),
            ).error,
        )
    }

    @Test
    fun legacyPreviewAndCorrectionSplitTheOldRepaymentWithoutSilentRewrite() = runTest {
        val legacy = Txn(
            id = "legacy",
            ledgerId = "l1",
            type = TransactionType.DEBT_REPAY,
            amount = Money.ofCents(101_000),
            accountId = "cash",
            toAccountId = "card",
            occurredAt = 1,
            title = "旧还款",
            draftStatus = DraftStatus.CONFIRMED,
            createdAt = 1,
            updatedAt = 1,
        )
        val oldPeriod = periods().first().copy(
            principal = Money.ofCents(100_000),
            interest = Money.ofCents(1_000),
            status = InstallmentPeriodStatus.PAID,
            paidTxnId = "legacy",
            paidAt = 1,
        )
        val fixture = fixture(periods = listOf(oldPeriod), txns = listOf(legacy))

        val preview = fixture.preview("plan")
        assertEquals(1, preview.size)
        assertEquals(Money.ofCents(101_000), preview.single().legacyAmount)
        assertEquals(Money.ofCents(101_000), preview.single().splitAmount)
        assertEquals(0, fixture.installments.linkSnapshot().size, "预览阶段不能写任何关联")
        assertNull(assertNotNull(fixture.txnRepo.getById("legacy")).deletedAt, "预览阶段不能软删历史流水")

        assertEquals(1, fixture.correct("plan").getOrThrow())
        val stored = assertNotNull(fixture.installments.getPeriod("plan", 1))
        assertEquals(Money.ofCents(100_000), stored.paidPrincipal)
        assertEquals(Money.ofCents(1_000), stored.paidInterest)
        assertTrue(assertNotNull(fixture.txnRepo.getById("legacy")).deletedAt != null, "旧流水软删保留在回收站")
        assertEquals(
            setOf(InstallmentPostingKind.PRINCIPAL, InstallmentPostingKind.INTEREST),
            fixture.installments.linkSnapshot().map { it.kind }.toSet(),
        )

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(fixture.correct("plan")).error)
    }
}