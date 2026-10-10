package com.han1997.vipledger.core.domain.calculator

import com.han1997.vipledger.core.domain.model.InvestmentLink
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/** 理财金额台账纯计算：只认未删除流水，投入/赎回与收益/损失分开汇总。 */
class InvestmentCalculatorTest {

    private fun txn(id: String, amount: Long, type: TransactionType, deleted: Boolean = false) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amount),
        accountId = "cash",
        occurredAt = 1,
        title = id,
        draftStatus = DraftStatus.CONFIRMED,
        createdAt = 1,
        updatedAt = 1,
        deletedAt = if (deleted) 2 else null,
    )

    private val links = listOf(
        InvestmentLink("t1", "inv", InvestmentLinkKind.INVEST, 1),
        InvestmentLink("t2", "inv", InvestmentLinkKind.REDEEM, 1),
        InvestmentLink("t3", "inv", InvestmentLinkKind.INCOME, 1),
        InvestmentLink("t4", "inv", InvestmentLinkKind.EXPENSE, 1),
        InvestmentLink("t5", "inv", InvestmentLinkKind.INVEST, 1),
    )

    private val txns = mapOf(
        "t1" to txn("t1", 100_000, TransactionType.TRANSFER),
        "t2" to txn("t2", 30_000, TransactionType.TRANSFER),
        "t3" to txn("t3", 5_000, TransactionType.INCOME),
        "t4" to txn("t4", 2_000, TransactionType.EXPENSE),
        "t5" to txn("t5", 50_000, TransactionType.TRANSFER, deleted = true),
    )

    @Test
    fun summaryIgnoresDeletedTransactions() {
        val summary = InvestmentCalculator.summarize(links, txns)
        assertEquals(Money.ofCents(100_000), summary.invested)
        assertEquals(Money.ofCents(30_000), summary.redeemed)
        assertEquals(Money.ofCents(5_000), summary.gain)
        assertEquals(Money.ofCents(2_000), summary.loss)
        assertEquals(Money.ofCents(70_000), summary.principalOutstanding)
        assertEquals(Money.ofCents(3_000), summary.realizedNet)
    }

    @Test
    fun redeemableMatchesTheOutstandingPrincipal() {
        assertEquals(Money.ofCents(70_000), InvestmentCalculator.redeemable(links, txns))
    }

    @Test
    fun missingTransactionsCountAsZero() {
        val summary = InvestmentCalculator.summarize(links, emptyMap())
        assertEquals(Money.ZERO, summary.invested)
        assertEquals(Money.ZERO, summary.principalOutstanding)
        assertEquals(Money.ZERO, summary.realizedNet)
    }
}