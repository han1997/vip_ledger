package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.fake.FakeInvestmentRepository
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.model.Investment
import com.han1997.vipledger.core.domain.model.InvestmentLink
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.investment.InvestmentTxnGuard
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** 理财流水的改/删守卫：方向与类型锁定，赎回累计不得超过投入。 */
class InvestmentTxnGuardTest {

    private val record = Investment(
        id = "inv",
        ledgerId = "l1",
        name = "基金",
        accountId = "inv-acc",
        createdAt = 0,
        updatedAt = 0,
    )

    private fun txn(
        id: String,
        type: TransactionType,
        amount: Long,
        accountId: String = "cash",
        toAccountId: String? = null,
    ) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amount),
        accountId = accountId,
        toAccountId = toAccountId,
        occurredAt = 1,
        title = id,
        draftStatus = DraftStatus.CONFIRMED,
        createdAt = 1,
        updatedAt = 1,
    )

    private class Fixture(
        record: Investment,
        txns: List<Txn>,
        links: List<InvestmentLink>,
    ) {
        val investments = FakeInvestmentRepository(mutableListOf(record), links.toMutableList())
        val txnRepo = FakeTxnRepository(txns.toMutableList())
        val guard = InvestmentTxnGuard(investments, txnRepo)
    }

    private fun fixture(
        txns: List<Txn> = listOf(
            txn("t-in", TransactionType.TRANSFER, 100_000, toAccountId = "inv-acc"),
            txn("t-out", TransactionType.TRANSFER, 30_000, accountId = "inv-acc"),
            txn("t-gain", TransactionType.INCOME, 5_000),
            txn("t-loss", TransactionType.EXPENSE, 2_000),
        ),
        links: List<InvestmentLink> = listOf(
            InvestmentLink("t-in", "inv", InvestmentLinkKind.INVEST, 1),
            InvestmentLink("t-out", "inv", InvestmentLinkKind.REDEEM, 1),
            InvestmentLink("t-gain", "inv", InvestmentLinkKind.INCOME, 1),
            InvestmentLink("t-loss", "inv", InvestmentLinkKind.EXPENSE, 1),
        ),
    ) = Fixture(record, txns, links)

    private fun message(error: AppError?): String? = (error as? AppError.Validation)?.message

    @Test
    fun unlinkedTransactionsPassThrough() = runTest {
        assertNull(fixture().guard.validate(txn("other", TransactionType.EXPENSE, 100)))
    }

    @Test
    fun investMustBeATransferIntoTheInvestmentAccount() = runTest {
        val fixture = fixture()
        assertEquals(
            InvestmentTxnGuard.DIRECTION,
            message(fixture.guard.validate(txn("t-in", TransactionType.TRANSFER, 100_000, toAccountId = "cash"))),
        )
        assertEquals(
            InvestmentTxnGuard.DIRECTION,
            message(fixture.guard.validate(txn("t-in", TransactionType.EXPENSE, 100_000))),
        )
    }

    @Test
    fun redeemMustBeATransferOutOfTheInvestmentAccount() = runTest {
        val fixture = fixture()
        assertEquals(
            InvestmentTxnGuard.DIRECTION,
            message(fixture.guard.validate(txn("t-out", TransactionType.TRANSFER, 30_000, accountId = "cash"))),
        )
    }

    @Test
    fun resultsMustMatchTheirType() = runTest {
        val fixture = fixture()
        assertEquals(
            InvestmentTxnGuard.DIRECTION,
            message(fixture.guard.validate(txn("t-gain", TransactionType.EXPENSE, 5_000))),
        )
        assertEquals(
            InvestmentTxnGuard.DIRECTION,
            message(fixture.guard.validate(txn("t-loss", TransactionType.INCOME, 2_000))),
        )
        assertNull(fixture.guard.validate(txn("t-gain", TransactionType.INCOME, 5_000)))
    }

    @Test
    fun redeemCannotExceedTheInvestedPrincipal() = runTest {
        val fixture = fixture()
        assertEquals(
            InvestmentTxnGuard.OVER_REDEEM,
            message(fixture.guard.validate(txn("t-out", TransactionType.TRANSFER, 200_000, accountId = "inv-acc"))),
        )
        assertNull(fixture.guard.validate(txn("t-out", TransactionType.TRANSFER, 100_000, accountId = "inv-acc")))
    }

    @Test
    fun danglingLinksAreRejected() = runTest {
        val fixture = fixture(links = listOf(InvestmentLink("t-in", "gone", InvestmentLinkKind.INVEST, 1)))
        assertEquals(
            InvestmentTxnGuard.LINK_INVALID,
            message(fixture.guard.validate(assertNotNull(txn("t-in", TransactionType.TRANSFER, 1, toAccountId = "inv-acc")))),
        )
    }
}