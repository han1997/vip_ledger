package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
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
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

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

    private fun setup(
        plans: List<InstallmentPlan> = listOf(plan),
        periods: List<InstallmentPeriod> = periods(),
    ): Triple<FakeInstallmentRepository, FakeTxnRepository, RecordingBalanceSyncer> {
        val installments = FakeInstallmentRepository(plans.toMutableList(), periods.toMutableList())
        val txns = FakeTxnRepository()
        val syncer = RecordingBalanceSyncer()
        return Triple(installments, txns, syncer)
    }

    private fun useCase(
        installments: FakeInstallmentRepository,
        txns: FakeTxnRepository,
        syncer: RecordingBalanceSyncer,
    ) = RepayInstallmentPeriodUseCase(
        installmentRepository = installments,
        txnRepository = txns,
        accountRepository = FakeAccountRepository(mutableListOf(card, cash)),
        categoryRepository = FakeCategoryRepository(),
        transactionRunner = FakeTransactionRunner(),
        balanceSyncer = syncer,
        timeProvider = FakeTimeProvider(),
        idGenerator = SequentialIdGenerator("txn"),
    )

    @Test
    fun repayingAPeriodWritesARepayTxnAndMarksItPaid() = runTest {
        val (installments, txns, syncer) = setup()
        val txn = assertIs<AppResult.Success<Txn>>(
            useCase(installments, txns, syncer)(
                RepayInstallmentPeriodInput(planId = "plan", periodNo = 1, fromAccountId = "cash"),
            ),
        ).value

        assertEquals(TransactionType.DEBT_REPAY, txn.type)
        assertEquals("cash", txn.accountId)
        assertEquals("card", txn.toAccountId)
        assertEquals(Money.ofYuan(1010), txn.amount)
        assertEquals("plan", txn.installmentPlanId)
        assertEquals(1, txn.installmentPeriodNo)
        assertEquals(1, txns.snapshot().size)

        val updated = assertNotNull(installments.getPeriod("plan", 1))
        assertEquals(InstallmentPeriodStatus.PAID, updated.status)
        assertEquals(txn.id, updated.paidTxnId)
        assertEquals(InstallmentPlanStatus.ACTIVE, assertNotNull(installments.getPlan("plan")).status)
        assertEquals(listOf("cash", "card"), syncer.syncedIds)
    }

    @Test
    fun payingTheLastPeriodClosesThePlan() = runTest {
        val (installments, txns, syncer) = setup()
        val useCase = useCase(installments, txns, syncer)
        useCase(RepayInstallmentPeriodInput(planId = "plan", periodNo = 1, fromAccountId = "cash"))
        useCase(RepayInstallmentPeriodInput(planId = "plan", periodNo = 2, fromAccountId = "cash"))

        assertEquals(InstallmentPlanStatus.CLOSED, assertNotNull(installments.getPlan("plan")).status)
        assertTrue(installments.periodSnapshot().all { it.status == InstallmentPeriodStatus.PAID })
        assertEquals(2, txns.snapshot().size)
    }

    @Test
    fun rejectsInvalidRepayments() = runTest {
        val (installments, txns, syncer) = setup()
        val useCase = useCase(installments, txns, syncer)
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
        assertIs<AppResult.Success<Txn>>(useCase(RepayInstallmentPeriodInput("plan", 1, "cash")))
        // 同一期不能还两次
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(useCase(RepayInstallmentPeriodInput("plan", 1, "cash"))).error,
        )
        // 最后一期还清后计划结清，再还任何一期都会被拒
        assertIs<AppResult.Success<Txn>>(useCase(RepayInstallmentPeriodInput("plan", 2, "cash")))
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(useCase(RepayInstallmentPeriodInput("plan", 2, "cash"))).error,
        )
    }

    @Test
    fun deletedPlansCannotBeRepaid() = runTest {
        val (installments, txns, syncer) = setup(plans = listOf(plan.copy(deletedAt = 1L)))
        assertIs<AppError.NotFound>(
            assertIs<AppResult.Failure>(
                useCase(installments, txns, syncer)(RepayInstallmentPeriodInput("plan", 1, "cash")),
            ).error,
        )
    }
}
