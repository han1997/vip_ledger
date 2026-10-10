package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.getOrThrow
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeInvestmentRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Investment
import com.han1997.vipledger.core.domain.usecase.investment.CloseInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.investment.CreateInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.investment.InvestMoreUseCase
import com.han1997.vipledger.core.domain.usecase.investment.ObserveInvestmentsUseCase
import com.han1997.vipledger.core.domain.usecase.investment.RecordInvestmentResultUseCase
import com.han1997.vipledger.core.domain.usecase.investment.RedeemInvestmentUseCase
import com.han1997.vipledger.core.domain.usecase.txn.CreateTxnUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.InvestmentLinkKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** 理财台账：投入/追加/赎回本金走转账，收益/损失走收支，赎回不得超在投本金。 */
class InvestmentUseCasesTest {

    private val cash = Account(
        id = "cash",
        ledgerId = "l1",
        name = "现金",
        type = AccountType.CASH,
        initialBalance = Money.ofCents(1_000_000),
        currentBalance = Money.ofCents(1_000_000),
        createdAt = 0,
        updatedAt = 0,
    )

    private class Fixture(cash: Account) {
        val investments = FakeInvestmentRepository()
        val txns = FakeTxnRepository()
        val accounts = FakeAccountRepository(mutableListOf(cash))
        val syncer = RecordingBalanceSyncer()
        val runner = FakeTransactionRunner()
        val time = FakeTimeProvider()
        private val ids = SequentialIdGenerator("id")
        private val createTxn = CreateTxnUseCase(
            txns,
            accounts,
            FakeCategoryRepository(),
            runner,
            syncer,
            time,
            ids,
        )

        val create = CreateInvestmentUseCase(investments, accounts, createTxn, runner, time, ids)
        val investMore = InvestMoreUseCase(investments, accounts, createTxn, runner, time)
        val redeem = RedeemInvestmentUseCase(investments, txns, accounts, createTxn, runner, time)
        val record = RecordInvestmentResultUseCase(investments, accounts, createTxn, runner, time)
        val close = CloseInvestmentUseCase(investments, runner, time)
        val observe = ObserveInvestmentsUseCase(investments, txns)
    }

    private fun fixture() = Fixture(cash)

    private suspend fun Fixture.openFund(): Investment =
        create("l1", "基金", "cash", Money.ofCents(100_000)).getOrThrow()

    @Test
    fun createPostsTheFirstTransferIntoANewInvestmentAccount() = runTest {
        val fixture = fixture()
        val record = fixture.openFund()

        val account = fixture.accounts.snapshot().first { it.id == record.accountId }
        assertEquals(AccountType.INVESTMENT, account.type)
        assertEquals("基金", account.name)

        val txn = fixture.txns.snapshot().single()
        assertEquals(TransactionType.TRANSFER, txn.type)
        assertEquals("cash", txn.accountId)
        assertEquals(record.accountId, txn.toAccountId)
        assertEquals(InvestmentLinkKind.INVEST, fixture.investments.linkSnapshot().single().kind)
    }

    @Test
    fun investmentAccountNameGetsASuffixWhenTaken() = runTest {
        val fixture = fixture()
        fixture.accounts.upsert(cash.copy(id = "other", name = "基金"))
        val record = fixture.openFund()
        assertEquals("基金-2", fixture.accounts.snapshot().first { it.id == record.accountId }.name)
    }

    @Test
    fun investMoreAndRedeemPostTransfersAndGuardTheOutstandingPrincipal() = runTest {
        val fixture = fixture()
        val record = fixture.openFund()
        fixture.investMore(record.id, "cash", Money.ofCents(50_000)).getOrThrow()

        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(fixture.redeem(record.id, "cash", Money.ofCents(200_000))).error,
        )
        val redeemed = fixture.redeem(record.id, "cash", Money.ofCents(150_000)).getOrThrow()
        assertEquals(record.accountId, redeemed.accountId)
        assertEquals("cash", redeemed.toAccountId)
        assertEquals(InvestmentLinkKind.REDEEM, fixture.investments.linkForTxn(redeemed.id)?.kind)

        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(fixture.redeem(record.id, "cash", Money.ofCents(1))).error,
        )
    }

    @Test
    fun resultsPostIncomeOrExpenseOnly() = runTest {
        val fixture = fixture()
        val record = fixture.openFund()

        val gain = fixture.record(record.id, "cash", Money.ofCents(5_000), InvestmentLinkKind.INCOME).getOrThrow()
        assertEquals(TransactionType.INCOME, gain.type)
        val loss = fixture.record(record.id, "cash", Money.ofCents(2_000), InvestmentLinkKind.EXPENSE).getOrThrow()
        assertEquals(TransactionType.EXPENSE, loss.type)

        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                fixture.record(record.id, "cash", Money.ofCents(1_000), InvestmentLinkKind.INVEST),
            ).error,
        )
    }

    @Test
    fun closingStopsFurtherInvesting() = runTest {
        val fixture = fixture()
        val record = fixture.openFund()
        fixture.close(record.id, closed = true).getOrThrow()

        assertTrue(fixture.investments.recordSnapshot().single().closed)
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                fixture.investMore(record.id, "cash", Money.ofCents(10_000)),
            ).error,
        )
    }

    @Test
    fun observeSummarizesPrincipalAndRealizedResults() = runTest {
        val fixture = fixture()
        val record = fixture.openFund()
        fixture.record(record.id, "cash", Money.ofCents(5_000), InvestmentLinkKind.INCOME).getOrThrow()
        fixture.record(record.id, "cash", Money.ofCents(2_000), InvestmentLinkKind.EXPENSE).getOrThrow()

        val progress = fixture.observe("l1").first().single()
        assertEquals(record.id, progress.investment.id)
        assertEquals(Money.ofCents(100_000), progress.summary.invested)
        assertEquals(Money.ofCents(100_000), progress.summary.principalOutstanding)
        assertEquals(Money.ofCents(3_000), progress.summary.realizedNet)
        assertTrue(progress.active)
        assertFalse(progress.investment.closed)
    }
}