package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.RecordingBalanceSyncer
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.AccountDraft
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.account.ArchiveAccountUseCase
import com.han1997.vipledger.core.domain.usecase.account.DeleteAccountUseCase
import com.han1997.vipledger.core.domain.usecase.account.SaveAccountUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AccountUseCasesTest {

    private val syncer = RecordingBalanceSyncer()
    private val runner = FakeTransactionRunner()

    private fun saveUseCase(accounts: FakeAccountRepository) = SaveAccountUseCase(
        accountRepository = accounts,
        transactionRunner = runner,
        balanceSyncer = syncer,
        timeProvider = FakeTimeProvider(),
        idGenerator = SequentialIdGenerator(),
    )

    private fun draft(
        name: String = "招行储蓄卡",
        type: AccountType = AccountType.BANK_DEBIT,
        initialBalance: Money = Money.ofCents(10000),
    ) = AccountDraft(ledgerId = "l1", name = name, type = type, initialBalance = initialBalance)

    @Test
    fun createCopiesInitialBalanceIntoCacheAndSyncsOnce() = runTest {
        val accounts = FakeAccountRepository()
        val created = assertIs<AppResult.Success<Account>>(saveUseCase(accounts).invoke(null, draft())).value

        assertEquals(Money.ofCents(10000), created.currentBalance)
        assertEquals(0, created.sortOrder)
        assertEquals(listOf(created.id), syncer.syncedIds)
        assertEquals(1, runner.transactionCount)
    }

    @Test
    fun createRejectsBlankNameAndInvalidBillingDay() = runTest {
        val accounts = FakeAccountRepository()
        val useCase = saveUseCase(accounts)

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(null, draft(name = "  "))).error)
        assertIs<AppError.Validation>(
            assertIs<AppResult.Failure>(
                useCase(null, draft(type = AccountType.CREDIT_CARD).copy(billingDay = 32)),
            ).error,
        )
        assertTrue(accounts.snapshot().isEmpty())
    }

    @Test
    fun updateRejectsDuplicateNameButIgnoresItself() = runTest {
        val accounts = FakeAccountRepository(
            mutableListOf(
                account("a1", "现金"),
                account("a2", "招行储蓄卡"),
            ),
        )
        val useCase = saveUseCase(accounts)

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase("a2", draft(name = "现金"))).error)
        assertIs<AppResult.Success<Account>>(useCase("a2", draft(name = "招行储蓄卡")))
    }

    @Test
    fun changingInitialBalanceTriggersRecomputeAndUnchangedDoesNot() = runTest {
        val accounts = FakeAccountRepository(mutableListOf(account("a1", "现金")))
        val useCase = saveUseCase(accounts)

        assertIs<AppResult.Success<Account>>(useCase("a1", draft(name = "现金", initialBalance = Money.ofCents(5000))))
        assertEquals(listOf("a1"), syncer.syncedIds)

        assertIs<AppResult.Success<Account>>(useCase("a1", draft(name = "日常现金", initialBalance = Money.ofCents(5000))))
        assertEquals(1, syncer.calls.size, "期初余额没变就不该重算余额")
    }

    @Test
    fun archivingLastUsableAccountIsRejected() = runTest {
        val accounts = FakeAccountRepository(mutableListOf(account("a1", "现金")))
        val useCase = ArchiveAccountUseCase(accounts, FakeTimeProvider())

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase("a1", true)).error)

        accounts.upsert(account("a2", "招行储蓄卡"))
        assertIs<AppResult.Success<Unit>>(useCase("a1", true))
    }

    @Test
    fun deletingAccountWithTxnsIsRejectedAndOthersSucceed() = runTest {
        val accounts = FakeAccountRepository(mutableListOf(account("a1", "现金"), account("a2", "招行储蓄卡")))
        val txns = FakeTxnRepository(
            mutableListOf(
                Txn(
                    id = "t1",
                    ledgerId = "l1",
                    type = TransactionType.EXPENSE,
                    amount = Money.ofCents(100),
                    accountId = "a1",
                    occurredAt = 0,
                    createdAt = 0,
                    updatedAt = 0,
                ),
            ),
        )
        val useCase = DeleteAccountUseCase(accounts, txns, FakeTimeProvider())

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase("a1")).error)
        assertIs<AppResult.Success<Unit>>(useCase("a2"))
        assertTrue(accounts.snapshot().first { it.id == "a2" }.deletedAt != null)
        assertNull(accounts.snapshot().first { it.id == "a1" }.deletedAt)
    }

    private fun account(id: String, name: String) = Account(
        id = id,
        ledgerId = "l1",
        name = name,
        type = AccountType.CASH,
        createdAt = 0,
        updatedAt = 0,
    )
}