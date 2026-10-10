package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.getOrThrow
import com.han1997.vipledger.core.domain.calculator.InstallmentAmounts
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeInstallmentRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanInput
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentOrigin
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SaveInstallmentPlanUseCaseTest {

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

    private fun repository() = FakeInstallmentRepository()

    private fun useCase(
        accounts: FakeAccountRepository = FakeAccountRepository(mutableListOf(card, cash)),
        txns: FakeTxnRepository = FakeTxnRepository(),
        repository: FakeInstallmentRepository = repository(),
    ) = SaveInstallmentPlanUseCase(
        installmentRepository = repository,
        accountRepository = accounts,
        transactionRunner = FakeTransactionRunner(),
        createTxn = CreateTxnUseCase(
            txns,
            accounts,
            FakeCategoryRepository(),
            FakeTransactionRunner(),
            RecordingBalanceSyncer(),
            FakeTimeProvider(),
            SequentialIdGenerator("txn"),
        ),
        timeProvider = FakeTimeProvider(),
        idGenerator = SequentialIdGenerator("plan"),
    )

    private fun input(
        method: InstallmentMethod = InstallmentMethod.EQUAL_PRINCIPAL,
        name: String = "装修分期",
        periods: Int = 12,
        dueDay: Int? = null,
        accountId: String = "card",
        customPeriodTotals: List<Money>? = null,
        customAmounts: List<InstallmentAmounts>? = null,
        origin: InstallmentOrigin = InstallmentOrigin.EXISTING,
        disbursementAccountId: String? = null,
    ) = SaveInstallmentPlanInput(
        ledgerId = "l1",
        accountId = accountId,
        name = name,
        method = method,
        principal = Money.ofCents(1_200_000),
        periods = periods,
        firstDueDate = LocalDate(2026, 1, 15),
        annualRateBp = 1200,
        dueDay = dueDay,
        origin = origin,
        disbursementAccountId = disbursementAccountId,
        customPeriodTotals = customPeriodTotals,
        customAmounts = customAmounts,
    )

    @Test
    fun createsAPlanWithOnePeriodPerInstallment() = runTest {
        val repository = repository()
        val plan = useCase(repository = repository)(input()).getOrThrow()
        assertEquals(1, repository.planCount())
        val periods = repository.periodSnapshot()
        assertEquals(12, periods.size)
        assertEquals(Money.ofCents(1_200_000), Money.sum(periods.map { it.principal }))
        assertEquals(LocalDate(2026, 1, 15), periods.first().dueDate)
        assertTrue(periods.all { it.status == InstallmentPeriodStatus.PENDING })
        assertEquals(plan.id, periods.first().planId)
        assertEquals(InstallmentOrigin.EXISTING, plan.origin)
        assertNull(plan.disbursedAt)
    }

    @Test
    fun existingPlanDoesNotPostPrincipal() = runTest {
        val txns = FakeTxnRepository()
        useCase(txns = txns)(input()).getOrThrow()
        assertTrue(txns.snapshot().isEmpty())
    }

    @Test
    fun disbursementPlanPostsPrincipalTransferAndStampsDisbursedAt() = runTest {
        val txns = FakeTxnRepository()
        val plan = useCase(txns = txns)(
            input(origin = InstallmentOrigin.DISBURSEMENT, disbursementAccountId = "cash"),
        ).getOrThrow()

        assertEquals(InstallmentOrigin.DISBURSEMENT, plan.origin)
        assertEquals("cash", plan.disbursementAccountId)
        assertNotNull(plan.disbursedAt)

        val txn = txns.snapshot().single()
        assertEquals(TransactionType.TRANSFER, txn.type)
        assertEquals(Money.ofCents(1_200_000), txn.amount)
        assertEquals("card", txn.accountId)
        assertEquals("cash", txn.toAccountId)
    }

    @Test
    fun disbursementPlanRequiresAUsableCashAccount() = runTest {
        val missing = useCase()(input(origin = InstallmentOrigin.DISBURSEMENT))
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(missing).error)
        val debt = useCase()(input(origin = InstallmentOrigin.DISBURSEMENT, disbursementAccountId = "card"))
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(debt).error)
        val unknown = useCase()(input(origin = InstallmentOrigin.DISBURSEMENT, disbursementAccountId = "nope"))
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(unknown).error)
    }

    @Test
    fun rejectsInvalidInputs() = runTest {
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase()(input(name = " "))).error)
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase()(input(accountId = "nope"))).error)
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase()(input(dueDay = 31))).error)
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase()(input(periods = 0))).error)
    }

    @Test
    fun customMethodRequiresExplicitAmounts() = runTest {
        val missing = useCase()(input(method = InstallmentMethod.CUSTOM))
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(missing).error)
    }

    @Test
    fun customAmountsUsePerPeriodPrincipalInterestAndFee() = runTest {
        val repository = repository()
        val amounts = listOf(
            InstallmentAmounts(Money.ofCents(600_000), Money.ofCents(6_000), Money.ofCents(1_000)),
            InstallmentAmounts(Money.ofCents(600_000), Money.ofCents(3_000), Money.ZERO),
        )
        useCase(repository = repository)(
            input(method = InstallmentMethod.CUSTOM, periods = 2, customAmounts = amounts),
        ).getOrThrow()

        val periods = repository.periodSnapshot()
        assertEquals(2, periods.size)
        assertEquals(Money.ofCents(1_200_000), Money.sum(periods.map { it.principal }))
        assertEquals(Money.ofCents(6_000), periods[0].interest)
        assertEquals(Money.ofCents(1_000), periods[0].fee)
        assertEquals(Money.ZERO, periods[1].fee)
    }

    @Test
    fun customAmountsMustSumToThePlanPrincipal() = runTest {
        val amounts = listOf(
            InstallmentAmounts(Money.ofCents(600_000), Money.ofCents(6_000)),
            InstallmentAmounts(Money.ofCents(500_000), Money.ofCents(3_000)),
        )
        val result = useCase()(input(method = InstallmentMethod.CUSTOM, periods = 2, customAmounts = amounts))
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(result).error)
    }
}