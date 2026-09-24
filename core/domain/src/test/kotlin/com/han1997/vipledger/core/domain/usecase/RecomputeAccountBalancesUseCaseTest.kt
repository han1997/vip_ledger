package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.RecomputeReport
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.account.RecomputeAccountBalancesUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RecomputeAccountBalancesUseCaseTest {

    private val stale = Account(
        id = "cash",
        ledgerId = "l1",
        name = "现金",
        type = AccountType.CASH,
        initialBalance = Money.ofCents(100_000),
        currentBalance = Money.ofCents(100_000),
        createdAt = 0,
        updatedAt = 0,
    )
    private val income = Txn(
        id = "i1",
        ledgerId = "l1",
        type = TransactionType.INCOME,
        amount = Money.ofCents(50_000),
        accountId = "cash",
        occurredAt = 0,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun recomputeFixesTheCachedBalanceAndReportsTheDiff() = runTest {
        val accounts = FakeAccountRepository(mutableListOf(stale))
        val txns = FakeTxnRepository(mutableListOf(income))
        val useCase = RecomputeAccountBalancesUseCase(accounts, txns, FakeTransactionRunner(), FakeTimeProvider())

        val report = assertIs<AppResult.Success<RecomputeReport>>(useCase("l1")).value
        assertEquals(1, report.checked)
        assertEquals(1, report.fixed.size)
        assertEquals(Money.ofCents(150_000), report.fixed.single().computed)
        assertEquals(Money.ofCents(50_000), report.fixed.single().delta)
        assertEquals(Money.ofCents(150_000), accounts.snapshot().single().currentBalance)
    }

    @Test
    fun recomputeIsSilentWhenTheCacheIsAlreadyCorrect() = runTest {
        val consistent = stale.copy(currentBalance = Money.ofCents(150_000))
        val accounts = FakeAccountRepository(mutableListOf(consistent))
        val txns = FakeTxnRepository(mutableListOf(income))
        val useCase = RecomputeAccountBalancesUseCase(accounts, txns, FakeTransactionRunner(), FakeTimeProvider())

        val report = assertIs<AppResult.Success<RecomputeReport>>(useCase("l1")).value
        assertTrue(report.fixed.isEmpty())
        assertEquals(0, report.checked - 1)
    }
}