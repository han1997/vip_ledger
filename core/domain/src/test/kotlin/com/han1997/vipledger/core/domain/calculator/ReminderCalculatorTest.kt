package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.LendingLink
import com.han1997.vipledger.core.domain.model.LendingProgress
import com.han1997.vipledger.core.domain.model.LendingRecord
import com.han1997.vipledger.core.domain.model.ReminderKind
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.LendingDirection
import com.han1997.vipledger.core.model.LendingLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ReminderCalculatorTest {

    private val today = LocalDate(2026, 1, 10)

    private val plan = InstallmentPlan(
        id = "plan",
        ledgerId = "l1",
        accountId = "card",
        name = "装修分期",
        method = InstallmentMethod.EQUAL_PRINCIPAL,
        principal = Money.ofYuan(12000),
        periods = 12,
        firstDueDate = LocalDate(2026, 1, 1),
        createdAt = 0,
        updatedAt = 0,
    )

    private fun period(no: Int, due: LocalDate, total: Money = Money.ofYuan(1000)) = InstallmentPeriod(
        id = "p$no",
        planId = "plan",
        periodNo = no,
        dueDate = due,
        principal = total,
        interest = Money.ZERO,
    )

    private fun account(
        id: String = "card",
        type: AccountType = AccountType.CREDIT_CARD,
        billingDay: Int? = null,
        dueDay: Int? = null,
        balance: Money = Money.ZERO,
    ) = Account(
        id = id,
        ledgerId = "l1",
        name = "信用卡",
        type = type,
        billingDay = billingDay,
        dueDay = dueDay,
        currentBalance = balance,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun installmentOverdueAndUpcomingAreBothReported() {
        val periods = mapOf(
            "plan" to listOf(
                period(1, LocalDate(2026, 1, 8)),
                period(2, LocalDate(2026, 1, 13)),
                period(3, LocalDate(2026, 2, 1)),
            ),
        )
        val reminders = ReminderCalculator.reminders(
            ledgerId = "l1",
            today = today,
            plans = listOf(plan),
            periodsByPlan = periods,
            accounts = emptyList(),
        )
        assertEquals(2, reminders.size)
        assertEquals(ReminderKind.INSTALLMENT_OVERDUE, reminders[0].kind)
        assertEquals(-2, reminders[0].daysUntilDue)
        assertEquals(1, reminders[0].periodNo)
        assertEquals(ReminderKind.INSTALLMENT_DUE, reminders[1].kind)
        assertEquals(3, reminders[1].daysUntilDue)
    }

    @Test
    fun closedPlansAndPaidPeriodsAreIgnored() {
        val closed = plan.copy(status = InstallmentPlanStatus.CLOSED)
        val periods = mapOf("plan" to listOf(period(1, LocalDate(2026, 1, 8))))
        assertTrue(
            ReminderCalculator.reminders("l1", today, listOf(closed), periods, emptyList()).isEmpty(),
        )
    }

    @Test
    fun creditCardBillingAndDueDatesFollowTheWindow() {
        val card = account(billingDay = 12, dueDay = 17, balance = Money.ofCents(-100_000))
        val reminders = ReminderCalculator.reminders(
            ledgerId = "l1",
            today = today,
            plans = emptyList(),
            periodsByPlan = emptyMap(),
            accounts = listOf(card),
        )
        assertEquals(listOf(ReminderKind.CARD_BILLING, ReminderKind.CARD_DUE), reminders.map { it.kind })
        assertEquals(2, reminders[0].daysUntilDue)
        assertEquals(7, reminders[1].daysUntilDue)
        assertEquals(Money.ofCents(100_000), reminders[1].amount)
    }

    @Test
    fun creditCardDueIsSkippedWhenThereIsNoDebt() {
        val card = account(billingDay = null, dueDay = 17, balance = Money.ZERO)
        assertTrue(
            ReminderCalculator.reminders("l1", today, emptyList(), emptyMap(), listOf(card)).isEmpty(),
        )
    }

    @Test
    fun monthlyDayBeyondTheMonthEndClampsToTheLastDay() {
        assertEquals(LocalDate(2026, 2, 28), ReminderCalculator.nextMonthlyDate(LocalDate(2026, 2, 27), 31))
        assertEquals(LocalDate(2026, 3, 20), ReminderCalculator.nextMonthlyDate(LocalDate(2026, 2, 27), 20))
        assertEquals(LocalDate(2026, 2, 20), ReminderCalculator.nextMonthlyDate(LocalDate(2026, 2, 20), 20))
    }

    private fun lending(
        id: String = "lending",
        due: LocalDate? = null,
        principal: Money = Money.ofYuan(1000),
        closed: Boolean = false,
    ) = LendingRecord(
        id = id,
        ledgerId = "l1",
        direction = LendingDirection.LEND,
        counterparty = "张三",
        principal = principal,
        accountId = "receivable",
        dueDate = due,
        closed = closed,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun lendingProgress(record: LendingRecord, settled: Money = Money.ZERO): LendingProgress =
        if (settled.isZero) {
            LendingProgress(record, emptyList(), emptyList())
        } else {
            LendingProgress(
                record,
                listOf(LendingLink("settle", record.id, LendingLinkKind.SETTLE, createdAt = 0)),
                listOf(
                    Txn(
                        id = "settle",
                        ledgerId = "l1",
                        type = TransactionType.TRANSFER,
                        amount = settled,
                        accountId = record.accountId,
                        toAccountId = "cash",
                        occurredAt = 0,
                        createdAt = 0,
                        updatedAt = 0,
                    ),
                ),
            )
        }

    @Test
    fun lendingDueAndOverdueFollowTheSameWindow() {
        val due = lending(due = LocalDate(2026, 1, 13))
        val overdue = lending(id = "late", due = LocalDate(2026, 1, 5))
        val far = lending(id = "far", due = LocalDate(2026, 3, 1))
        val noDue = lending(id = "nodue")
        val reminders = ReminderCalculator.reminders(
            ledgerId = "l1",
            today = today,
            plans = emptyList(),
            periodsByPlan = emptyMap(),
            accounts = emptyList(),
            lendings = listOf(lendingProgress(due), lendingProgress(overdue), lendingProgress(far), lendingProgress(noDue)),
        )
        assertEquals(listOf(ReminderKind.LENDING_OVERDUE, ReminderKind.LENDING_DUE), reminders.map { it.kind })
        assertEquals(-5, reminders[0].daysUntilDue)
        assertEquals("late", reminders[0].lendingId)
        assertEquals(3, reminders[1].daysUntilDue)
        assertEquals(Money.ofYuan(1000), reminders[1].amount)
        assertEquals("lending", reminders[1].lendingId)
    }

    @Test
    fun settledOrClosedLendingsAreNotReminded() {
        val settled = lendingProgress(lending(due = LocalDate(2026, 1, 13)), settled = Money.ofYuan(1000))
        val closed = lendingProgress(lending(id = "closed", due = LocalDate(2026, 1, 13), closed = true))
        assertTrue(
            ReminderCalculator.reminders("l1", today, emptyList(), emptyMap(), emptyList(), listOf(settled, closed)).isEmpty(),
        )
    }
}
