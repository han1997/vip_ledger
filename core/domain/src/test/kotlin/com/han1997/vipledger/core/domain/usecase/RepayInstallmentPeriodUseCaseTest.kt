package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.getOrThrow
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
import com.han1997.vipledger.core.domain.usecase.loan.RepayInstallmentPeriodInput
import com.han1997.vipledger.core.domain.usecase.loan.RepayInstallmentPeriodUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
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

/**
 * 逐期核销：本金走转账（现金 → 负债），利息/手续费走支出；一期可产生 1–3 条流水。
 */
class RepayInstallmentPeriodUseCaseTest {

    private val card = Account(
        id = "card",
        ledgerId = "l1",
        name = "信用卡",
        type = AccountType.CREDIT_CARD,
        createdAt = 0,
        updatedAt = 0,
    )
    private val cash = Account(
        id = "cash",
        ledgerId = "l1",
        name = "现金",
        type = AccountType.CASH,
        createdAt = 0,
        updatedAt = 0,
    )

    private val plan = InstallmentPlan(
        id = "plan",
        ledgerId = "l1",
        accountId = "card",
        name = "装修分期",
        method = InstallmentMethod.EQUAL_PRINCIPAL,
        principal = Money.ofYuan(2000),
        periods = 2,
        firstDueDate = LocalDate(2026, 1, 15),
        createdAt = 0,
        updatedAt = 0,
    )

    private fun periods() = listOf(
        InstallmentPeriod(
            id = "p1",
            planId = "plan",
            periodNo = 1,
            dueDate = LocalDate(2026, 1, 15),
            principal = Money.ofYuan(1000),
            interest = Money.ofYuan(10),
        ),
        InstallmentPeriod(
            id = "p2",
            planId = "plan",
            periodNo = 2,
            dueDate = LocalDate(2026, 2, 15),
            principal = Money.ofYuan(1000),
            interest = Money.ofYuan(5),
        ),
    )

    private class Fixture(
        plans: List<InstallmentPlan>,
        periods: List<InstallmentPeriod>,
        card: Account,
        cash: Account,
    ) {
        val installments = FakeInstallmentRepository(plans.toMutableList(), periods.toMutableList())
        val txns = FakeTxnRepository()
        val accounts = FakeAccountRepository(mutableListOf(card, cash))
        val syncer = RecordingBalanceSyncer()
        val runner = FakeTransactionRunner()
        private val time = FakeTimeProvider()
        private val create = CreateTxnUseCase(
            txns,
            accounts,
            FakeCategoryRepository(),
            runner,
            syncer,
            time,
            SequentialIdGenerator("txn"),
        )
        val useCase = RepayInstallmentPeriodUseCase(installments, accounts, runner, create, FakeTimeProvider())
    }

    private fun fixture(
        plans: List<InstallmentPlan> = listOf(plan),
        periods: List<InstallmentPeriod> = periods(),
    ) = Fixture(plans, periods, card, cash)

    @Test
    fun repayingAPeriodSplitsPrincipalTransferAndInterestExpense() = runTest {
        val fixture = fixture()
        val entries = fixture.useCase(
            RepayInstallmentPeriodInput(planId = "plan", periodNo = 1, fromAccountId = "cash"),
        ).getOrThrow()

        assertEquals(2, entries.size)
        val principal = entries.single { it.type == TransactionType.TRANSFER }
        val interest = entries.single { it.type == TransactionType.EXPENSE }
        assertEquals(Money.ofYuan(1000), principal.amount)
        assertEquals("cash", principal.accountId)
        assertEquals("card", principal.toAccountId)
        assertEquals("plan", principal.installmentPlanId)
        assertEquals(1, principal.installmentPeriodNo)
        assertEquals(Money.ofYuan(10), interest.amount)
        assertEquals("cash", interest.accountId)
        assertNull(interest.toAccountId)

        val links = fixture.installments.linkSnapshot()
        assertEquals(setOf(InstallmentPostingKind.PRINCIPAL, InstallmentPostingKind.INTEREST), links.map { it.kind }.toSet())

        val updated = assertNotNull(fixture.installments.getPeriod("plan", 1))
        assertEquals(InstallmentPeriodStatus.PAID, updated.status)
        assertEquals(Money.ofYuan(1000), updated.paidPrincipal)
        assertEquals(Money.ofYuan(10), updated.paidInterest)
        assertEquals(Money.ZERO, updated.paidFee)
        assertEquals(principal.id, updated.paidTxnId)
        assertEquals(InstallmentPlanStatus.ACTIVE, assertNotNull(fixture.installments.getPlan("plan")).status)
    }

    @Test
    fun partialRepaymentAllocatesFeeThenInterestAndKeepsThePeriodPartial() = runTest {
        val withFee = periods().map { if (it.periodNo == 1) it.copy(fee = Money.ofYuan(3)) else it }
        val fixture = fixture(periods = withFee)
        fixture.useCase(
            RepayInstallmentPeriodInput(
                planId = "plan",
                periodNo = 1,
                fromAccountId = "cash",
                amount = Money.ofYuan(8),
            ),
        ).getOrThrow()

        val updated = assertNotNull(fixture.installments.getPeriod("plan", 1))
        assertEquals(InstallmentPeriodStatus.PARTIAL, updated.status)
        assertEquals(Money.ofYuan(3), updated.paidFee)
        assertEquals(Money.ofYuan(5), updated.paidInterest)
        assertEquals(Money.ZERO, updated.paidPrincipal)
        assertTrue(fixture.txns.snapshot().all { it.type == TransactionType.EXPENSE })
        assertEquals(InstallmentPlanStatus.ACTIVE, assertNotNull(fixture.installments.getPlan("plan")).status)
    }

    @Test
    fun payingTheLastPeriodClosesThePlan() = runTest {
        val fixture = fixture()
        fixture.useCase(RepayInstallmentPeriodInput("plan", 1, "cash")).getOrThrow()
        fixture.useCase(RepayInstallmentPeriodInput("plan", 2, "cash")).getOrThrow()

        assertEquals(InstallmentPlanStatus.CLOSED, assertNotNull(fixture.installments.getPlan("plan")).status)
        assertTrue(fixture.installments.periodSnapshot().all { it.status == InstallmentPeriodStatus.PAID })
        assertEquals(4, fixture.txns.snapshot().size)
    }

    @Test
    fun rejectsInvalidRepayments() = runTest {
        val fixture = fixture()
        val useCase = fixture.useCase
        assertIs<AppError.NotFound>(
            assertIs<AppResult.Failure>(useCase(RepayInstallmentPeriodInput("nope", 1, "cash"))).error,
        )
        assertIs<AppError.NotFound>(
            assertIs<AppResult.Failure>(useCase(RepayInstallmentPeriodInput("plan", 9, "cash"))).error,
        )
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(useCase(RepayInstallmentPeriodInput("plan", 1, "nope"))).error,
        )
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(useCase(RepayInstallmentPeriodInput("plan", 1, "card"))).error,
        )
        // 超过本期未还
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                useCase(
                    RepayInstallmentPeriodInput("plan", 1, "cash", amount = Money.ofYuan(9999)),
                ),
            ).error,
        )
        assertIs<AppResult.Success<List<Txn>>>(useCase(RepayInstallmentPeriodInput("plan", 1, "cash")))
        // 同一期不能还两次
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(useCase(RepayInstallmentPeriodInput("plan", 1, "cash"))).error,
        )
        // 最后一期还清后计划结清，再还任何一期都会被拒
        assertIs<AppResult.Success<List<Txn>>>(useCase(RepayInstallmentPeriodInput("plan", 2, "cash")))
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(useCase(RepayInstallmentPeriodInput("plan", 2, "cash"))).error,
        )
    }

    @Test
    fun deletedPlansCannotBeRepaid() = runTest {
        val fixture = fixture(plans = listOf(plan.copy(deletedAt = 1L)))
        assertIs<AppError.NotFound>(
            assertIs<AppResult.Failure>(
                fixture.useCase(RepayInstallmentPeriodInput("plan", 1, "cash")),
            ).error,
        )
    }
}