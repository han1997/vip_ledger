package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.model.GenerateMode
import com.han1997.vipledger.core.model.MissedPolicy
import com.han1997.vipledger.core.model.RecurrenceEndType
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class RecurrenceCalculatorTest {

    private fun rule(
        freq: RecurrenceFrequency = RecurrenceFrequency.DAILY,
        interval: Int = 1,
        start: LocalDate = LocalDate(2026, 1, 1),
        byWeekday: Int? = null,
        byMonthDay: Int? = null,
        byMonth: Int? = null,
        endType: RecurrenceEndType = RecurrenceEndType.NEVER,
        endDate: LocalDate? = null,
        maxOccurrences: Int? = null,
        generatedCount: Int = 0,
        enabled: Boolean = true,
    ) = RecurringRule(
        id = "rule",
        ledgerId = "l1",
        name = "房租",
        txnType = TransactionType.EXPENSE,
        amount = Money.ofYuan(2000),
        accountId = "cash",
        freq = freq,
        interval = interval,
        byWeekday = byWeekday,
        byMonthDay = byMonthDay,
        byMonth = byMonth,
        startAt = start.toDateColumnEpochMillis(),
        endType = endType,
        endDate = endDate?.toDateColumnEpochMillis(),
        maxOccurrences = maxOccurrences,
        generatedCount = generatedCount,
        generateMode = GenerateMode.DRAFT,
        missedPolicy = MissedPolicy.SKIP,
        enabled = enabled,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun dailyStepsByDayAndIsIndexBased() {
        val r = rule(freq = RecurrenceFrequency.DAILY)
        assertEquals(LocalDate(2026, 1, 1), RecurrenceCalculator.occurrenceAt(r, 0))
        assertEquals(LocalDate(2026, 1, 6), RecurrenceCalculator.occurrenceAt(r, 5))
        assertEquals(10, RecurrenceCalculator.dueOccurrences(r, LocalDate(2026, 1, 10)).size)
    }

    @Test
    fun everyNDaysAndEveryNMonthsUseTheInterval() {
        val everyThreeDays = rule(freq = RecurrenceFrequency.EVERY_N_DAYS, interval = 3)
        assertEquals(LocalDate(2026, 1, 7), RecurrenceCalculator.occurrenceAt(everyThreeDays, 2))

        val quarterly = rule(
            freq = RecurrenceFrequency.EVERY_N_MONTHS,
            interval = 3,
            byMonthDay = 1,
            start = LocalDate(2026, 1, 1),
        )
        assertEquals(LocalDate(2026, 1, 1), RecurrenceCalculator.occurrenceAt(quarterly, 0))
        assertEquals(LocalDate(2026, 4, 1), RecurrenceCalculator.occurrenceAt(quarterly, 1))
    }

    @Test
    fun weeklyAnchorsToTheRequestedWeekday() {
        // 2026-01-01 是周四，指定周一则锚点顺延到 2026-01-05。
        val r = rule(freq = RecurrenceFrequency.WEEKLY, byWeekday = 0, start = LocalDate(2026, 1, 1))
        assertEquals(LocalDate(2026, 1, 5), RecurrenceCalculator.anchor(r))
        assertEquals(LocalDate(2026, 1, 12), RecurrenceCalculator.occurrenceAt(r, 1))
    }

    @Test
    fun monthlyKeepsTheIntendedDayInsteadOfDriftingFromTheClampedDate() {
        val r = rule(freq = RecurrenceFrequency.MONTHLY, byMonthDay = 31, start = LocalDate(2026, 1, 31))
        assertEquals(LocalDate(2026, 1, 31), RecurrenceCalculator.occurrenceAt(r, 0))
        assertEquals(LocalDate(2026, 2, 28), RecurrenceCalculator.occurrenceAt(r, 1))
        assertEquals(LocalDate(2026, 3, 31), RecurrenceCalculator.occurrenceAt(r, 2))
    }

    @Test
    fun monthlyHandlesLeapFebruary() {
        val r = rule(freq = RecurrenceFrequency.MONTHLY, byMonthDay = 31, start = LocalDate(2024, 1, 31))
        assertEquals(LocalDate(2024, 2, 29), RecurrenceCalculator.occurrenceAt(r, 1))
    }

    @Test
    fun monthlyWithoutDayUsesTheStartDayAndMovesForwardWhenNeeded() {
        val sameDay = rule(freq = RecurrenceFrequency.MONTHLY, start = LocalDate(2026, 1, 31))
        assertEquals(LocalDate(2026, 2, 28), RecurrenceCalculator.occurrenceAt(sameDay, 1))

        val laterDay = rule(freq = RecurrenceFrequency.MONTHLY, byMonthDay = 15, start = LocalDate(2026, 1, 20))
        assertEquals(LocalDate(2026, 2, 15), RecurrenceCalculator.occurrenceAt(laterDay, 0))
        assertEquals(LocalDate(2026, 3, 15), RecurrenceCalculator.occurrenceAt(laterDay, 1))
    }

    @Test
    fun yearlyUsesMonthAndDay() {
        val r = rule(
            freq = RecurrenceFrequency.YEARLY,
            byMonth = 3,
            byMonthDay = 10,
            start = LocalDate(2026, 1, 5),
        )
        assertEquals(LocalDate(2026, 3, 10), RecurrenceCalculator.occurrenceAt(r, 0))
        assertEquals(LocalDate(2027, 3, 10), RecurrenceCalculator.occurrenceAt(r, 1))
    }

    @Test
    fun countEndStopsAfterMaxOccurrences() {
        val r = rule(
            freq = RecurrenceFrequency.DAILY,
            endType = RecurrenceEndType.COUNT,
            maxOccurrences = 3,
        )
        assertEquals(3, RecurrenceCalculator.dueOccurrences(r, LocalDate(2026, 1, 31)).size)
        assertNull(RecurrenceCalculator.nextOccurrenceDate(r, 3))
        assertEquals(LocalDate(2026, 1, 3), RecurrenceCalculator.nextOccurrenceDate(r, 2))
    }

    @Test
    fun untilDateEndStopsAtTheBoundary() {
        val r = rule(
            freq = RecurrenceFrequency.DAILY,
            endType = RecurrenceEndType.UNTIL_DATE,
            endDate = LocalDate(2026, 1, 5),
        )
        val due = RecurrenceCalculator.dueOccurrences(r, LocalDate(2026, 1, 31))
        assertEquals(5, due.size)
        assertEquals(LocalDate(2026, 1, 5), due.last())
        assertNull(RecurrenceCalculator.nextOccurrenceDate(r, 5))
    }

    @Test
    fun dueOccurrencesStartFromGeneratedCountAndRespectTheCap() {
        val r = rule(freq = RecurrenceFrequency.DAILY, generatedCount = 3)
        val due = RecurrenceCalculator.dueOccurrences(r, LocalDate(2026, 1, 5))
        assertEquals(listOf(LocalDate(2026, 1, 4), LocalDate(2026, 1, 5)), due)

        val longRunning = rule(freq = RecurrenceFrequency.DAILY, start = LocalDate(2020, 1, 1))
        assertEquals(
            RecurrenceCalculator.MAX_CATCH_UP,
            RecurrenceCalculator.dueOccurrences(longRunning, LocalDate(2026, 1, 1)).size,
        )
    }

    @Test
    fun disabledRulesProduceNothing() {
        val r = rule(enabled = false)
        assertTrue(RecurrenceCalculator.dueOccurrences(r, LocalDate(2026, 1, 10)).isEmpty())
    }
}
