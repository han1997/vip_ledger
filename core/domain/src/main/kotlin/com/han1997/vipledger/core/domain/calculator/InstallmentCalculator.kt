package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 生成分期计划需要的输入（docs/DATA-MODEL.md 3.7）。 */
data class InstallmentScheduleInput(
    val principal: Money,
    val periods: Int,
    val firstDueDate: LocalDate,
    val annualRateBp: Int = 0,
    /** 每期还款日（1–28）；为空时沿用 [firstDueDate] 的日。 */
    val dueDay: Int? = null,
    val upfrontFee: Money = Money.ZERO,
    val perPeriodFee: Money = Money.ZERO,
)

/** 计算出来的单期（尚未落库）。 */
data class ScheduledPeriod(
    val periodNo: Int,
    val dueDate: LocalDate,
    val principal: Money,
    val interest: Money,
    val fee: Money,
) {
    val total: Money get() = principal + interest + fee
}

/** 自定义分期的单期金额（本金/利息/费分别录入）。 */
data class InstallmentAmounts(
    val principal: Money,
    val interest: Money = Money.ZERO,
    val fee: Money = Money.ZERO,
)

/**
 * 分期计息（docs/DATA-MODEL.md 3.7）。
 *
 * 约定：
 * - 每期利息 = 剩余本金 × 年化利率 / 12，四舍五入到分（用 BigDecimal，不引入浮点误差）；
 * - 各期本金之和恰好等于本金，除不尽的余额全部落在末期（末期兜底）；
 * - 手续费按 upfront（一次性）与每期固定值计入，不参与本金摊销。
 */
object InstallmentCalculator {

    const val MONTHS_PER_YEAR = 12
    private const val BP_PER_UNIT = 10_000L

    /** 年化基点转每期（月）利率，仅用于等额本息的年金公式。 */
    fun monthlyRateRatio(annualRateBp: Int): Double = annualRateBp.toDouble() / BP_PER_UNIT / MONTHS_PER_YEAR

    fun isValidInput(input: InstallmentScheduleInput): Boolean =
        input.principal.isPositive && input.periods > 0 && input.annualRateBp >= 0 &&
            input.upfrontFee.cents >= 0 && input.perPeriodFee.cents >= 0

    fun schedule(method: InstallmentMethod, input: InstallmentScheduleInput): List<ScheduledPeriod> {
        require(isValidInput(input)) { "本金需大于 0、期数需大于 0、利率与手续费不能为负" }
        return when (method) {
            InstallmentMethod.EQUAL_INSTALLMENT -> equalInstallment(input)
            InstallmentMethod.EQUAL_PRINCIPAL -> equalPrincipal(input)
            InstallmentMethod.INTEREST_FIRST -> interestFirst(input)
            InstallmentMethod.CUSTOM -> throw IllegalArgumentException("自定义分期需要传入每期还款额，请用 scheduleCustom")
        }
    }

    /**
     * 自定义每期还款额：用户给出每期的「还款总额」，利息仍按剩余本金计算，
     * 本金部分 = 总额 − 利息 − 手续费，末期用剩余本金兜底。
     */
    fun scheduleCustom(input: InstallmentScheduleInput, periodTotals: List<Money>): List<ScheduledPeriod> {
        require(isValidInput(input)) { "本金需大于 0、期数需大于 0、利率与手续费不能为负" }
        require(periodTotals.size == input.periods) { "自定义分期需要给出每一期的还款额" }
        require(periodTotals.all { it.isPositive }) { "每期还款额必须大于 0" }
        var remaining = input.principal
        return buildList(input.periods) {
            for (no in 1..input.periods) {
                val interest = monthlyInterest(remaining, input.annualRateBp)
                val principalPart = if (no == input.periods) {
                    remaining
                } else {
                    val computed = periodTotals[no - 1] - interest - input.perPeriodFee
                    if (computed.cents <= 0L) {
                        throw IllegalArgumentException("第 $no 期还款额不足以覆盖利息，请调高金额或降低利率")
                    }
                    if (computed > remaining) remaining else computed
                }
                remaining -= principalPart
                add(period(no, input, principalPart, interest))
            }
            require(remaining.isZero) { "各期本金之和必须等于本金" }
        }
    }

    /**
     * 自定义分期：逐期直接给出本金/利息/费，不再从「每期还款总额」反推本金。
     * 校验各期本金之和恰好等于计划本金，避免账面漏计或多计。
     */
    fun scheduleCustomAmounts(
        input: InstallmentScheduleInput,
        entries: List<InstallmentAmounts>,
    ): List<ScheduledPeriod> {
        require(isValidInput(input)) { "本金需大于 0、期数需大于 0、利率与手续费不能为负" }
        require(entries.size == input.periods) { "自定义分期需要给出每一期的金额" }
        require(entries.all { !it.principal.isNegative && !it.interest.isNegative && !it.fee.isNegative }) {
            "每期本金/利息/费不能为负"
        }
        require(Money.sum(entries.map { it.principal }) == input.principal) {
            "各期本金之和必须等于本金"
        }
        return entries.mapIndexed { index, entry ->
            ScheduledPeriod(
                periodNo = index + 1,
                dueDate = dueDateOf(input, index + 1),
                principal = entry.principal,
                interest = entry.interest,
                fee = entry.fee,
            )
        }
    }

    private fun equalInstallment(input: InstallmentScheduleInput): List<ScheduledPeriod> {
        val payment = equalPayment(input)
        var remaining = input.principal
        return buildList(input.periods) {
            for (no in 1..input.periods) {
                val interest = monthlyInterest(remaining, input.annualRateBp)
                val principalPart = if (no == input.periods) {
                    remaining
                } else {
                    require(payment > interest) { "每期还款额不足以覆盖利息，请增加期数" }
                    (payment - interest).coerceAtMost(remaining)
                }
                remaining -= principalPart
                add(period(no, input, principalPart, interest))
            }
            require(remaining.isZero) { "各期本金之和必须等于本金" }
        }
    }

    private fun equalPrincipal(input: InstallmentScheduleInput): List<ScheduledPeriod> {
        val baseCents = input.principal.cents / input.periods
        var remaining = input.principal
        return buildList(input.periods) {
            for (no in 1..input.periods) {
                val principalPart = if (no == input.periods) remaining else Money.ofCents(baseCents)
                val interest = monthlyInterest(remaining, input.annualRateBp)
                remaining -= principalPart
                add(period(no, input, principalPart, interest))
            }
        }
    }

    private fun interestFirst(input: InstallmentScheduleInput): List<ScheduledPeriod> {
        val interest = monthlyInterest(input.principal, input.annualRateBp)
        return buildList(input.periods) {
            for (no in 1..input.periods) {
                val principalPart = if (no == input.periods) input.principal else Money.ZERO
                add(period(no, input, principalPart, interest))
            }
        }
    }

    private fun period(
        no: Int,
        input: InstallmentScheduleInput,
        principalPart: Money,
        interest: Money,
    ): ScheduledPeriod = ScheduledPeriod(
        periodNo = no,
        dueDate = dueDateOf(input, no),
        principal = principalPart,
        interest = interest,
        fee = input.perPeriodFee,
    )

    /** 等额本息的每期还款额：P·r / (1 − (1+r)^-n)，零利率退化为等额本金。 */
    private fun equalPayment(input: InstallmentScheduleInput): Money {
        val rate = monthlyRateRatio(input.annualRateBp)
        if (input.annualRateBp == 0) return Money.ofCents(input.principal.cents / input.periods)
        val discount = 1 - Math.pow(1 + rate, -input.periods.toDouble())
        val payment = input.principal.cents * rate / discount
        return Money.ofCents(BigDecimal.valueOf(payment).setScale(0, RoundingMode.HALF_UP).toLong())
    }

    /** 每期利息 = 剩余本金 × 年化基点 / (10000 × 12)，HALF_UP 到分。 */
    private fun monthlyInterest(remaining: Money, annualRateBp: Int): Money {
        if (annualRateBp == 0 || remaining.cents <= 0L) return Money.ZERO
        val value = BigDecimal.valueOf(remaining.cents)
            .multiply(BigDecimal.valueOf(annualRateBp.toLong()))
            .divide(BigDecimal.valueOf(BP_PER_UNIT * MONTHS_PER_YEAR), 0, RoundingMode.HALF_UP)
        return Money.ofCents(value.toLong())
    }

    /**
     * 还款日：首期沿用用户选的 [InstallmentScheduleInput.firstDueDate]，
     * 之后按月推进；指定了 dueDay 就钳到该月的那一天（该月不足时取月末）。
     */
    private fun dueDateOf(input: InstallmentScheduleInput, no: Int): LocalDate {
        if (no == 1) return input.firstDueDate
        val shifted = input.firstDueDate.plus(no - 1, DateTimeUnit.MONTH)
        val day = input.dueDay ?: return shifted
        val lastDay = LocalDate(shifted.year, shifted.monthNumber, 1)
            .plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
        return LocalDate(shifted.year, shifted.monthNumber, minOf(day, lastDay))
    }

    private fun Money.coerceAtMost(other: Money): Money = if (this > other) other else this
}
