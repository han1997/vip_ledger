package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.model.*
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.*

class RefundAccountingTest {
    private val zone = TimeZone.UTC
    private val purchase = Txn("purchase", "ledger", TransactionType.EXPENSE, Money.ofCents(10000), "cash",
        occurredAt = LocalDate(2026, 9, 1).startOfDayEpochMillis(zone), createdAt = 1, updatedAt = 1)
    private val refund = purchase.copy(id = "refund", type = TransactionType.REFUND, amount = Money.ofCents(3000),
        refundOfTxnId = purchase.id, occurredAt = LocalDate(2026, 10, 1).startOfDayEpochMillis(zone))

    @Test fun crossMonthRefundIncreasesBalanceAndReducesOnlyOctoberExpense() {
        val txns = listOf(purchase, refund)
        val october = DateRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31))
        val summary = StatsCalculator.summarize(txns, emptyList(), emptyList(), october, zone)
        assertEquals(Money.ofCents(-3000), summary.expense)
        assertEquals(Money.ZERO, summary.income)
        assertEquals(Money.ofCents(3000), BalanceCalculator.deltaOf(refund, "cash"))
        assertEquals(Money.ofCents(-7000), BalanceCalculator.deltaFor("cash", txns))
        assertEquals(summary.expense, TxnDayGrouper.countedSum(listOf(refund), TransactionType.EXPENSE))
        assertEquals(summary.expense, StatsCalculator.byPeriod(txns, PeriodGranularity.MONTH, october, zone).single().expense)
        assertEquals(summary.expense, summary.byCategory.getValue(CategoryKind.EXPENSE).single().total)
    }
    @Test fun partialRefundsCannotExceedPurchaseEvenOnEditOrRestore() {
        assertNull(RefundValidator.validate(refund, listOf(purchase)))
        val another = refund.copy(id = "second", amount = Money.ofCents(8000))
        assertNotNull(RefundValidator.validate(another, listOf(purchase, refund)))
        assertNotNull(RefundValidator.validate(purchase.copy(amount = Money.ofCents(2000)), listOf(purchase, refund)))
        assertNotNull(RefundValidator.validate(purchase.copy(deletedAt = 2), listOf(purchase, refund)))
        assertNull(RefundValidator.validate(refund.copy(refundOfTxnId = null), emptyList()))
    }
}
