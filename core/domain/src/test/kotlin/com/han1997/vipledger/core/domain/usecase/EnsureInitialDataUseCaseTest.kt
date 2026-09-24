package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeLedgerRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.support.BuiltinCategory
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class EnsureInitialDataUseCaseTest {

    private val builtin = object : BuiltinCategorySource {
        override fun categories(): List<BuiltinCategory> = listOf(
            BuiltinCategory(
                name = "餐饮",
                kind = CategoryKind.EXPENSE,
                children = listOf(
                    BuiltinCategory("早餐", CategoryKind.EXPENSE),
                    BuiltinCategory("午餐", CategoryKind.EXPENSE),
                ),
            ),
            BuiltinCategory("工资", CategoryKind.INCOME),
        )
    }

    private fun useCase(
        ledgers: FakeLedgerRepository = FakeLedgerRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
    ) = EnsureInitialDataUseCase(
        ledgerRepository = ledgers,
        accountRepository = accounts,
        categoryRepository = categories,
        builtinCategorySource = builtin,
        transactionRunner = FakeTransactionRunner(),
        timeProvider = FakeTimeProvider(),
        idGenerator = SequentialIdGenerator(),
    )

    @Test
    fun emptyDatabaseGetsADefaultLedgerAccountAndTwoLevelCategories() = runTest {
        val ledgers = FakeLedgerRepository()
        val accounts = FakeAccountRepository()
        val categories = FakeCategoryRepository()
        val useCase = useCase(ledgers, accounts, categories)

        val ledger = assertIs<AppResult.Success<Ledger>>(useCase()).value
        assertTrue(ledger.isDefault)
        assertEquals(EnsureInitialDataUseCase.DEFAULT_LEDGER_NAME, ledger.name)

        val account = accounts.snapshot().single()
        assertEquals(AccountType.CASH, account.type)
        assertEquals(ledger.id, account.ledgerId)

        val stored = categories.snapshot()
        assertEquals(4, stored.size)
        val tops = stored.filter { it.parentId == null }
        assertEquals(2, tops.size)
        assertTrue(tops.all { it.isSystem })
        val breakfast = stored.first { it.name == "早餐" }
        assertEquals(tops.first { it.name == "餐饮" }.id, breakfast.parentId)
    }

    @Test
    fun repeatedCallsAreIdempotent() = runTest {
        val ledgers = FakeLedgerRepository()
        val accounts = FakeAccountRepository()
        val categories = FakeCategoryRepository()
        val useCase = useCase(ledgers, accounts, categories)

        val first = assertIs<AppResult.Success<Ledger>>(useCase()).value
        val second = assertIs<AppResult.Success<Ledger>>(useCase()).value
        assertEquals(first.id, second.id)
        assertEquals(1, ledgers.count())
        assertEquals(1, accounts.snapshot().size)
        assertEquals(4, categories.snapshot().size)
    }

    @Test
    fun existingLedgerWithoutDefaultFlagIsPromoted() = runTest {
        val existing = Ledger(id = "l1", name = "旧账本", isDefault = false, createdAt = 0, updatedAt = 0)
        val ledgers = FakeLedgerRepository(mutableListOf(existing))
        val accounts = FakeAccountRepository()
        val useCase = useCase(ledgers, accounts)

        val ledger = assertIs<AppResult.Success<Ledger>>(useCase()).value
        assertEquals("l1", ledger.id)
        assertTrue(assertIs<AppResult.Success<Ledger>>(AppResult.success(ledgers.getDefault()!!)).value.isDefault)
        assertTrue(accounts.snapshot().isEmpty())
    }
}