package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** 未还期次重算：部分提前还款与利率变更只动「完全未还」的尾部期次。 */
class InstallmentScheduleRewriteTest {

    private val plan = InstallmentPlan(
        id = "plan",
        ledgerId = "l1",
        accountId = "card",
        name = "房贷",
        method = InstallmentMethod.EQUAL_PRINCIPAL,
        principal = Money.ofCents(1_200_000),
        periods = 4,
        annualRateBp = 1200,
        firstDueDate = LocalDate(2026, 1, 15),
        createdAt = 0,
        updatedAt = 0,
    )

    private fun schedule(source: InstallmentPlan = plan): List<InstallmentPeriod> =
        InstallmentCalculator.schedule(
            source.method,
            InstallmentScheduleInput(
                principal = source.principal,
                periods = source.periods,
                firstDueDate = source.firstDueDate,
                annualRateBp = source.annualRateBp,
            ),
        ).mapIndexed { index, scheduled ->
            InstallmentPeriod(
                id = "p${index + 1}",
                planId = source.id,
                periodNo = scheduled.periodNo,
                dueDate = scheduled.dueDate,
                principal = scheduled.principal,
                interest = scheduled.interest,
                fee = scheduled.fee,
            )
        }

    private fun InstallmentPeriod.markPaid(): InstallmentPeriod = copy(
        status = InstallmentPeriodStatus.PAID,
        paidPrincipal = principal,
        paidInterest = interest,
        paidFee = fee,
        paidTxnId = "txn-$periodNo",
        paidAt = 1,
    )

    @Test
    fun prepayShrinksTailPrincipalAndKeepsCountAndDates() {
        val periods = schedule()
        val withPaid = periods.map { if (it.periodNo == 1) it.markPaid() else it }
        val reduction = Money.ofCents(100_000)

        val rewritten = InstallmentScheduleRewrite.rewriteTail(
            plan = plan,
            periods = withPaid,
            tailStartIndex = 1,
            principalReduction = reduction,
        )

        assertEquals(4, rewritten.size)
        assertEquals(withPaid[0], rewritten[0], "已还期次必须原样保留")
        assertEquals(
            Money.sum(withPaid.drop(1).map { it.principal }) - reduction,
            Money.sum(rewritten.drop(1).map { it.principal }),
        )
        assertEquals(withPaid.map { it.periodNo }, rewritten.map { it.periodNo })
        assertEquals(withPaid.map { it.dueDate }, rewritten.map { it.dueDate })
        // 等额本金重算后每期本金仍然平均
        assertTrue(rewritten.drop(1).map { it.principal }.distinct().size <= 2)
    }

    @Test
    fun prepayRejectsAmountsThatExceedOrMatchTheOutstandingPrincipal() {
        val periods = schedule()
        assertFailsWith<IllegalArgumentException> {
            InstallmentScheduleRewrite.rewriteTail(
                plan = plan,
                periods = periods,
                tailStartIndex = 0,
                principalReduction = plan.principal,
            )
        }
    }

    @Test
    fun rewriteRejectsTailsContainingAlreadyTouchedPeriods() {
        val periods = schedule().map { if (it.periodNo == 2) it.markPaid() else it }
        assertFailsWith<IllegalArgumentException> {
            InstallmentScheduleRewrite.rewriteTail(plan, periods, tailStartIndex = 1)
        }
    }

    @Test
    fun changeRateOnlyRecomputesInterestFromTheChosenPeriodOnward() {
        val periods = schedule()
        val rewritten = InstallmentScheduleRewrite.changeRate(plan, periods, fromPeriodNo = 3, newRateBp = 2400)

        assertEquals(periods[0].interest, rewritten[0].interest)
        assertEquals(periods[1].interest, rewritten[1].interest)
        assertTrue(rewritten[2].interest > periods[2].interest, "利率翻倍后利息应上升")
        assertTrue(rewritten[3].interest > periods[3].interest)
        assertEquals(Money.ofCents(1_200_000), Money.sum(rewritten.map { it.principal }))
        assertEquals(periods.map { it.dueDate }, rewritten.map { it.dueDate })
    }

    @Test
    fun customTailIsReducedFromTheLastPeriodBackwards() {
        val custom = plan.copy(method = InstallmentMethod.CUSTOM)
        val periods = listOf(
            InstallmentPeriod("c1", custom.id, 1, LocalDate(2026, 1, 15), Money.ofCents(300_000), Money.ZERO),
            InstallmentPeriod("c2", custom.id, 2, LocalDate(2026, 2, 15), Money.ofCents(300_000), Money.ZERO),
            InstallmentPeriod("c3", custom.id, 3, LocalDate(2026, 3, 15), Money.ofCents(300_000), Money.ZERO),
            InstallmentPeriod("c4", custom.id, 4, LocalDate(2026, 4, 15), Money.ofCents(300_000), Money.ZERO),
        )

        val rewritten = InstallmentScheduleRewrite.rewriteTail(
            plan = custom,
            periods = periods,
            tailStartIndex = 2,
            principalReduction = Money.ofCents(50_000),
        )

        assertEquals(periods[0], rewritten[0])
        assertEquals(periods[1], rewritten[1])
        assertEquals(periods[2], rewritten[2])
        assertEquals(Money.ofCents(250_000), rewritten[3].principal)
    }
}