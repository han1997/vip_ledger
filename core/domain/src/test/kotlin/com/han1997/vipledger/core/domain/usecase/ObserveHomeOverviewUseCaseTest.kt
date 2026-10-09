package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeLedgerRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.home.ObserveHomeOverviewUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ObserveHomeOverviewUseCaseTest {

    private val timeProvider = FakeTimeProvider()
    private val now = timeProvider.now

    private val ledger = Ledger(id = "l1", name = "默认账本", isDefault = true, createdAt = 0, updatedAt = 0)

    private fun useCase(
        accounts: FakeAccountRepository,
        txns: FakeTxnRepository,
    ) = ObserveHomeOverviewUseCase(
        ledgerRepository = FakeLedgerRepository(mutableListOf(ledger)),
        accountRepository = accounts,
        txnRepository = txns,
        timeProvider = timeProvider,
    )

    @Test
    fun netWorthComesFromCacheAndMonthTotalsOnlyCountConfirmedPublishedTxns() = runTest {
        val accounts = FakeAccountRepository(
            mutableListOf(
                account("a1", AccountType.CASH, 100000),
                account("a2", AccountType.CREDIT_CARD, -30000),
                account("a3", AccountType.BANK_DEBIT, 5000, archived = true),
            ),
        )
        val txns = FakeTxnRepository(
            mutableListOf(
                txn("t1", TransactionType.EXPENSE, 3800, now),
                txn("t2", TransactionType.INCOME, 800000, now),
                txn("t3", TransactionType.TRANSFER, 20000, now),
                txn("t4", TransactionType.EXPENSE, 9999, now, draftStatus = DraftStatus.PENDING),
                txn("t5", TransactionType.EXPENSE, 50000, now - 40L * 24 * 3600 * 1000),
            ),
        )

        val overview = useCase(accounts, txns)("l1").first()

        assertEquals("默认账本", overview.ledgerName)
        assertEquals(Money.ofCents(70000), overview.netWorth, "归档账户不计入净资产")
        assertEquals(Money.ofCents(30000), overview.totalDebt)
        assertEquals(Money.ofCents(800000), overview.monthIncome)
        assertEquals(Money.ofCents(3800), overview.monthExpense)
        assertEquals(Money.ofCents(3800), overview.todayExpense)
        assertEquals(2, overview.monthCount, "只有本月已确认的收支入账")
        assertEquals(2, overview.usableAccountCount)
        assertEquals(4, overview.recentTxns.size, "首页近期流水不混入待确认草稿")
        assertEquals(Money.ofCents(100000), overview.totalAssets)
        assertEquals(Money.ofCents(800000), overview.todayIncome)
    }

    @Test
    fun emptyLedgerReportsZeros() = runTest {
        val accounts = FakeAccountRepository(mutableListOf(account("a1", AccountType.CASH, 0)))
        val overview = useCase(accounts, FakeTxnRepository())("l1").first()

        assertEquals(Money.ZERO, overview.netWorth)
        assertEquals(Money.ZERO, overview.monthExpense)
        assertEquals(Money.ZERO, overview.monthBalance)
        assertTrue(overview.recentTxns.isEmpty())
    }

    private fun account(
        id: String,
        type: AccountType,
        balanceCents: Long,
        archived: Boolean = false,
    ) = Account(
        id = id,
        ledgerId = "l1",
        name = id,
        type = type,
        currentBalance = Money.ofCents(balanceCents),
        isArchived = archived,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun txn(
        id: String,
        type: TransactionType,
        amountCents: Long,
        occurredAt: Long,
        draftStatus: DraftStatus = DraftStatus.CONFIRMED,
    ) = Txn(
        id = id,
        ledgerId = "l1",
        type = type,
        amount = Money.ofCents(amountCents),
        accountId = "a1",
        toAccountId = if (type == TransactionType.TRANSFER) "a2" else null,
        occurredAt = occurredAt,
        draftStatus = draftStatus,
        createdAt = 0,
        updatedAt = 0,
    )
}
