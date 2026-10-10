package com.han1997.vipledger.core.domain.usecase.reminder

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.calculator.ReminderCalculator
import com.han1997.vipledger.core.domain.model.Reminder
import com.han1997.vipledger.core.domain.repository.AccountRepository
import com.han1997.vipledger.core.domain.repository.InstallmentRepository
import com.han1997.vipledger.core.domain.usecase.lending.ObserveLendingsUseCase
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 账本下的提醒流：分期临近 / 逾期 + 信用卡账单日 / 还款日。
 *
 * 计划或账户任何一个变化都会重算；`today` 取调用时的设备日期。
 */
class ObserveRemindersUseCase(
    private val installmentRepository: InstallmentRepository,
    private val accountRepository: AccountRepository,
    private val observeLendings: ObserveLendingsUseCase,
    private val timeProvider: TimeProvider,
    private val windowDays: Int = ReminderCalculator.DEFAULT_WINDOW_DAYS,
) {

    operator fun invoke(ledgerId: String): Flow<List<Reminder>> = combine(
        installmentRepository.observePlans(ledgerId),
        accountRepository.observeByLedger(ledgerId, includeArchived = false),
        observeLendings(ledgerId),
    ) { plans, accounts, lendings -> Triple(plans, accounts, lendings) }
        .map { (plans, accounts, lendings) ->
            val today = timeProvider.nowMillis().toLocalDate(timeProvider.zone)
            val periodsByPlan = plans.associate { plan -> plan.id to installmentRepository.listPeriods(plan.id) }
            ReminderCalculator.reminders(
                ledgerId = ledgerId,
                today = today,
                plans = plans,
                periodsByPlan = periodsByPlan,
                accounts = accounts,
                lendings = lendings,
                windowDays = windowDays,
            )
        }
}
