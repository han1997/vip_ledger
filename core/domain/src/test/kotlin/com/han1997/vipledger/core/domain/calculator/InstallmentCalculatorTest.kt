package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InstallmentCalculatorTest {

    private fun input(
        principalCents: Long = 1_200_000,
        periods: Int = 12,
        annualRateBp: Int = 1200,
        dueDay: Int? = null,
        firstDueDate: LocalDate = LocalDate(2026, 1, 15),
    ) = InstallmentScheduleInput(
        principal = Money.ofCents(principalCents),
        periods = periods,
        annualRateBp = annualRateBp,
        firstDueDate = firstDueDate,
        dueDay = dueDay,
    )

    @Test
    fun equalInstallmentKeepsPaymentFlatAndPrincipalExact() {
        val schedule = InstallmentCalculator.schedule(InstallmentMethod.EQUAL_INSTALLMENT, input())
        assertEquals(12, schedule.size)
        assertEquals(Money.ofCents(1_200_000), Money.sum(schedule.map { it.principal }))
        val flat = schedule.dropLast(1).map { it.total }.distinct()
        assertEquals(1, flat.size, "前 11 期还款额应完全相同")
        assertTrue(schedule.first().total in Money.ofCents(106_600)..Money.ofCents(106_640))
        assertTrue(schedule.zipWithNext().all { (a, b) -> b.interest <= a.interest })
        assertTrue(schedule.zipWithNext().all { (a, b) -> b.principal >= a.principal })
        assertEquals(Money.ofCents(12_000), schedule.first().interest)
    }

    @Test
    fun zeroRateDegradesToEvenSplit() {
        val schedule = InstallmentCalculator.schedule(
            InstallmentMethod.EQUAL_INSTALLMENT,
            input(principalCents = 1_200_000, periods = 12, annualRateBp = 0),
        )
        assertEquals(Money.ofCents(100_000), schedule.first().total)
        assertEquals(Money.ofCents(1_200_000), Money.sum(schedule.map { it.principal }))
        assertTrue(schedule.all { it.interest.isZero })
        assertEquals(Money.ofCents(1_200_000), Money.sum(schedule.map { it.total }))
    }

    @Test
    fun equalPrincipalPutsRemainderInTheLastPeriod() {
        val schedule = InstallmentCalculator.schedule(
            InstallmentMethod.EQUAL_PRINCIPAL,
            input(principalCents = 100_000, periods = 3, annualRateBp = 1200),
        )
        assertEquals(listOf(Money.ofCents(33_333), Money.ofCents(33_333), Money.ofCents(33_334)), schedule.map { it.principal })
        assertEquals(Money.ofCents(100_000), Money.sum(schedule.map { it.principal }))
        assertEquals(listOf(Money.ofCents(1_000), Money.ofCents(667), Money.ofCents(333)), schedule.map { it.interest })
    }

    @Test
    fun interestFirstPaysPrincipalOnlyAtTheEnd() {
        val schedule = InstallmentCalculator.schedule(
            InstallmentMethod.INTEREST_FIRST,
            input(principalCents = 1_200_000, periods = 12, annualRateBp = 1200),
        )
        assertEquals(Money.ZERO, schedule.first().principal)
        assertEquals(Money.ofCents(1_200_000), schedule.last().principal)
        assertEquals(Money.ofCents(12_000), schedule.first().interest)
        assertEquals(Money.ofCents(1_200_000), Money.sum(schedule.map { it.principal }))
        assertTrue(schedule.all { it.interest == Money.ofCents(12_000) })
    }

    @Test
    fun customScheduleUsesGivenTotalsAndBalancesTheLastPeriod() {
        val schedule = InstallmentCalculator.scheduleCustom(
            input(principalCents = 1_200_000, periods = 3, annualRateBp = 0),
            listOf(Money.ofCents(400_000), Money.ofCents(400_000), Money.ofCents(400_000)),
        )
        assertEquals(Money.ofCents(1_200_000), Money.sum(schedule.map { it.principal }))
        assertEquals(Money.ofCents(400_000), schedule.first().total)
    }

    @Test
    fun customScheduleRejectsAmountsBelowInterest() {
        assertFailsWith<IllegalArgumentException> {
            InstallmentCalculator.scheduleCustom(
                input(principalCents = 1_200_000, periods = 3, annualRateBp = 1200),
                listOf(Money.ofCents(1_000), Money.ofCents(400_000), Money.ofCents(800_000)),
            )
        }
    }

    @Test
    fun customMethodNeedsExplicitAmounts() {
        assertFailsWith<IllegalArgumentException> {
            InstallmentCalculator.schedule(InstallmentMethod.CUSTOM, input())
        }
    }

    @Test
    fun rejectsNonPositivePrincipalOrPeriods() {
        assertFailsWith<IllegalArgumentException> {
            InstallmentCalculator.schedule(InstallmentMethod.EQUAL_PRINCIPAL, input(principalCents = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            InstallmentCalculator.schedule(InstallmentMethod.EQUAL_PRINCIPAL, input(periods = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            InstallmentCalculator.schedule(InstallmentMethod.EQUAL_PRINCIPAL, input(annualRateBp = -1))
        }
    }

    @Test
    fun dueDayDrivesEveryPeriodAfterTheFirstAndClampsToMonthEnd() {
        val schedule = InstallmentCalculator.schedule(
            InstallmentMethod.EQUAL_PRINCIPAL,
            input(principalCents = 300_000, periods = 3, annualRateBp = 0, dueDay = 31, firstDueDate = LocalDate(2026, 1, 15)),
        )
        assertEquals(LocalDate(2026, 1, 15), schedule[0].dueDate)
        assertEquals(LocalDate(2026, 2, 28), schedule[1].dueDate)
        assertEquals(LocalDate(2026, 3, 31), schedule[2].dueDate)
    }

    @Test
    fun upfrontAndPerPeriodFeesDoNotAffectPrincipalSplit() {
        val withFees = InstallmentScheduleInput(
            principal = Money.ofCents(1_200_000),
            periods = 12,
            annualRateBp = 1200,
            firstDueDate = LocalDate(2026, 1, 15),
            upfrontFee = Money.ofCents(50_000),
            perPeriodFee = Money.ofCents(1_000),
        )
        val schedule = InstallmentCalculator.schedule(InstallmentMethod.EQUAL_PRINCIPAL, withFees)
        assertEquals(Money.ofCents(1_200_000), Money.sum(schedule.map { it.principal }))
        assertEquals(Money.ofCents(1_000), schedule.first().fee)
        assertEquals(Money.ofCents(50_000), withFees.upfrontFee)
    }
}