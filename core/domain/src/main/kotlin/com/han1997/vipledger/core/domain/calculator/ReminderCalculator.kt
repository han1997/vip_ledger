package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.LendingProgress
import com.han1997.vipledger.core.domain.model.Reminder
import com.han1997.vipledger.core.domain.model.ReminderKind
import com.han1997.vipledger.core.model.InstallmentPeriodStatus
import com.han1997.vipledger.core.model.InstallmentPlanStatus
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * 提醒计算（docs/DATA-MODEL.md 3.2 / 3.7）。
 *
 * 应用内提醒卡与后台通知都走这一个函数，避免两处口径漂移。
 * 时间语义：`today` 是设备时区下的今天；[windowDays] 是「还有几天算临近」。
 */
object ReminderCalculator {

    const val DEFAULT_WINDOW_DAYS: Int = 7

    fun reminders(
        ledgerId: String,
        today: LocalDate,
        plans: List<InstallmentPlan>,
        periodsByPlan: Map<String, List<InstallmentPeriod>>,
        accounts: List<Account>,
        lendings: List<LendingProgress> = emptyList(),
        windowDays: Int = DEFAULT_WINDOW_DAYS,
    ): List<Reminder> {
        val result = ArrayList<Reminder>()

        plans.asSequence()
            .filter { it.status == InstallmentPlanStatus.ACTIVE && it.deletedAt == null }
            .forEach { plan ->
                val pending = periodsByPlan[plan.id].orEmpty()
                    .filter { it.status == InstallmentPeriodStatus.PENDING }
                pending.forEach { period ->
                    val days = today.daysUntil(period.dueDate)
                    if (days > windowDays) return@forEach
                    result += Reminder(
                        kind = if (days < 0) ReminderKind.INSTALLMENT_OVERDUE else ReminderKind.INSTALLMENT_DUE,
                        ledgerId = ledgerId,
                        dueDate = period.dueDate,
                        daysUntilDue = days,
                        amount = period.total,
                        name = plan.name,
                        planId = plan.id,
                        periodNo = period.periodNo,
                    )
                }
            }

        accounts.asSequence()
            .filter { it.ledgerId == ledgerId && it.deletedAt == null && it.type.hasBillingCycle }
            .forEach { account ->
                account.billingDay?.let { day ->
                    val date = nextMonthlyDate(today, day)
                    val days = today.daysUntil(date)
                    if (days <= windowDays) {
                        result += Reminder(
                            kind = ReminderKind.CARD_BILLING,
                            ledgerId = ledgerId,
                            dueDate = date,
                            daysUntilDue = days,
                            amount = Money.ZERO,
                            name = account.name,
                            accountId = account.id,
                        )
                    }
                }
                account.dueDay?.let { day ->
                    if (!account.currentBalance.isNegative) return@let
                    val date = nextMonthlyDate(today, day)
                    val days = today.daysUntil(date)
                    if (days <= windowDays) {
                        result += Reminder(
                            kind = ReminderKind.CARD_DUE,
                            ledgerId = ledgerId,
                            dueDate = date,
                            daysUntilDue = days,
                            amount = -account.currentBalance,
                            name = account.name,
                            accountId = account.id,
                        )
                    }
                }
            }

        lendings.asSequence()
            .filter { it.active }
            .forEach { progress ->
                val due = progress.record.dueDate ?: return@forEach
                val days = today.daysUntil(due)
                if (days <= windowDays) {
                    result += Reminder(
                        kind = if (days < 0) ReminderKind.LENDING_OVERDUE else ReminderKind.LENDING_DUE,
                        ledgerId = ledgerId,
                        dueDate = due,
                        daysUntilDue = days,
                        amount = progress.outstanding,
                        name = progress.record.counterparty,
                        lendingId = progress.record.id,
                    )
                }
            }

        return result.sortedWith(compareBy({ it.dueDate }, { it.kind.ordinal }))
    }

    /** 从 [from] 起（含当天）该月第 [dayOfMonth] 天的下一个日子，超出当月天数时取当月最后一天。 */
    fun nextMonthlyDate(from: LocalDate, dayOfMonth: Int): LocalDate {
        val thisMonth = RecurrenceCalculator.dateInMonth(from.year, from.monthNumber, dayOfMonth)
        if (thisMonth >= from) return thisMonth
        val monthIndex = from.year * 12 + from.monthNumber
        val year = monthIndex / 12
        val month = monthIndex % 12 + 1
        return RecurrenceCalculator.dateInMonth(year, month, dayOfMonth)
    }
}
