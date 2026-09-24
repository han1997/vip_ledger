package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeInstallmentRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanInput
import com.han1997.vipledger.core.domain.usecase.loan.SaveInstallmentPlanUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    private fun useCase(accounts: FakeAccountRepository = FakeAccountRepository(mutableListOf(card))) =
        SaveInstallmentPlanUseCase(
            installmentRepository = FakeInstallmentRepository(),
            accountRepository = accounts,
            transactionRunner = FakeTransactionRunner(),
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
        customPeriodTotals = customPeriodTotals,
    )

    @Test
    fun createsAPlanWithOnePeriodPerInstallment() = runTest {
        val repository = FakeInstallmentRepository()
        val useCase = SaveInstallmentPlanUseCase(
            installmentRepository = repository,
            accountRepository = FakeAccountRepository(mutableListOf(card)),
            transactionRunner = FakeTransactionRunner(),
            timeProvider = FakeTimeProvider(),
            idGenerator = SequentialIdGenerator("plan"),
        )
        val plan = assertIs<AppResult.Success<InstallmentPlan>>(useCase(input())).value
        assertEquals(1, repository.planCount())
        val periods = repository.periodSnapshot()
        assertEquals(12, periods.size)
        assertEquals(Money.ofCents(1_200_000), Money.sum(periods.map { it.principal }))
        assertEquals(LocalDate(2026, 1, 15), periods.first().dueDate)
        assertTrue(periods.all { it.status == InstallmentPeriodStatus.PENDING })
        assertEquals(plan.id, periods.first().planId)
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
}