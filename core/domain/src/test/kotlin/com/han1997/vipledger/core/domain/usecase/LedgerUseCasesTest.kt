package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeAccountRepository
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeCurrentLedgerStore
import com.han1997.vipledger.core.domain.fake.FakeLedgerRepository
import com.han1997.vipledger.core.domain.fake.FakeTagRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.support.BuiltinCategory
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.usecase.ledger.ArchiveLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.CreateLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.DeleteLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.SelectLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.SetDefaultLedgerUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.UpdateLedgerUseCase
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class LedgerUseCasesTest {

    private val builtin = object : BuiltinCategorySource {
        override fun categories(): List<BuiltinCategory> = listOf(
            BuiltinCategory(
                name = "餐饮",
                kind = CategoryKind.EXPENSE,
                children = listOf(BuiltinCategory("午餐", CategoryKind.EXPENSE)),
            ),
            BuiltinCategory("工资", CategoryKind.INCOME),
        )
    }

    private fun createUseCase(
        ledgers: FakeLedgerRepository = FakeLedgerRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        runner: FakeTransactionRunner = FakeTransactionRunner(),
    ) = CreateLedgerUseCase(
        ledgerRepository = ledgers,
        accountRepository = accounts,
        categoryRepository = categories,
        builtinCategorySource = builtin,
        transactionRunner = runner,
        timeProvider = FakeTimeProvider(),
        idGenerator = SequentialIdGenerator(),
    )

    @Test
    fun firstLedgerIsDefaultAndBringsCashAccountAndBuiltinCategories() = runTest {
        val ledgers = FakeLedgerRepository()
        val accounts = FakeAccountRepository()
        val categories = FakeCategoryRepository()
        val runner = FakeTransactionRunner()

        val ledger = assertIs<AppResult.Success<Ledger>>(createUseCase(ledgers, accounts, categories, runner)("旅行")).value

        assertTrue(ledger.isDefault)
        assertEquals("旅行", ledger.name)
        assertEquals(1, runner.transactionCount, "账本与初始数据必须写在同一个事务里")
        assertEquals(AccountType.CASH, accounts.snapshot().single().type)
        assertEquals(3, categories.snapshot().size)
    }

    @Test
    fun secondLedgerIsNotDefault() = runTest {
        val ledgers = FakeLedgerRepository(mutableListOf(ledger("l1", "默认账本", isDefault = true)))
        val created = assertIs<AppResult.Success<Ledger>>(
            createUseCase(ledgers = ledgers)("旅行"),
        ).value
        assertFalse(created.isDefault)
        assertEquals(1, created.sortOrder)
    }

    @Test
    fun createRejectsBlankAndDuplicatedNames() = runTest {
        val ledgers = FakeLedgerRepository(mutableListOf(ledger("l1", "默认账本", isDefault = true)))
        val useCase = createUseCase(ledgers = ledgers)

        val blank = assertIs<AppResult.Failure>(useCase("   "))
        assertIs<AppError.Validation>(blank.error)

        val duplicated = assertIs<AppResult.Failure>(useCase("默认账本"))
        assertIs<AppError.Validation>(duplicated.error)
    }

    @Test
    fun updateRenamesLedgerButRejectsDuplicates() = runTest {
        val ledgers = FakeLedgerRepository(
            mutableListOf(ledger("l1", "默认账本", isDefault = true), ledger("l2", "旅行")),
        )
        val useCase = UpdateLedgerUseCase(ledgers, FakeTimeProvider())

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase("l1", "旅行")).error)
        assertIs<AppResult.Success<Ledger>>(useCase("l1", "日常"))
        assertEquals("日常", ledgers.getById("l1")?.name)
    }

    @Test
    fun archiveRejectsDefaultLedgerAndLastActiveLedger() = runTest {
        val ledgers = FakeLedgerRepository(
            mutableListOf(ledger("l1", "默认账本", isDefault = true), ledger("l2", "旅行")),
        )
        val useCase = ArchiveLedgerUseCase(ledgers, FakeTimeProvider())

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase("l1", true)).error)
        assertIs<AppResult.Success<Unit>>(useCase("l2", true))
        assertTrue(ledgers.getById("l2")!!.isArchived)
        // l2 已归档，此时只剩默认账本，再归档它一样要被拒
        assertIs<AppResult.Success<Unit>>(useCase("l2", false))
        assertFalse(ledgers.getById("l2")!!.isArchived)
    }

    @Test
    fun deleteRejectsLedgerWithTxnsAndCleansUpDependentsOtherwise() = runTest {
        val ledgers = FakeLedgerRepository(
            mutableListOf(ledger("l1", "默认账本", isDefault = true), ledger("l2", "旅行")),
        )
        val accounts = FakeAccountRepository(
            mutableListOf(
                Account(id = "a1", ledgerId = "l2", name = "现金", type = AccountType.CASH, createdAt = 0, updatedAt = 0),
            ),
        )
        val categories = FakeCategoryRepository(
            mutableListOf(
                Category(id = "c1", ledgerId = "l2", name = "餐饮", kind = CategoryKind.EXPENSE, createdAt = 0, updatedAt = 0),
            ),
        )
        val tags = FakeTagRepository(mutableListOf(Tag(id = "g1", ledgerId = "l2", name = "报销", createdAt = 0, updatedAt = 0)))
        val txns = FakeTxnRepository(
            mutableListOf(
                Txn(
                    id = "t1",
                    ledgerId = "l2",
                    type = TransactionType.EXPENSE,
                    amount = Money.ofCents(100),
                    accountId = "a1",
                    occurredAt = 0,
                    createdAt = 0,
                    updatedAt = 0,
                ),
            ),
        )
        val useCase = DeleteLedgerUseCase(
            ledgerRepository = ledgers,
            accountRepository = accounts,
            categoryRepository = categories,
            tagRepository = tags,
            txnRepository = txns,
            transactionRunner = FakeTransactionRunner(),
            timeProvider = FakeTimeProvider(),
        )

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase("l2")).error)

        txns.softDelete("t1", 1L)
        assertIs<AppResult.Success<Unit>>(useCase("l2"))
        assertTrue(accounts.snapshot().single().deletedAt != null)
        assertTrue(categories.snapshot().single().deletedAt != null)
        assertEquals(null, ledgers.getById("l2"))
    }

    @Test
    fun defaultLedgerCannotBeDeleted() = runTest {
        val ledgers = FakeLedgerRepository(
            mutableListOf(ledger("l1", "默认账本", isDefault = true), ledger("l2", "旅行")),
        )
        val useCase = DeleteLedgerUseCase(
            ledgerRepository = ledgers,
            accountRepository = FakeAccountRepository(),
            categoryRepository = FakeCategoryRepository(),
            tagRepository = FakeTagRepository(),
            txnRepository = FakeTxnRepository(),
            transactionRunner = FakeTransactionRunner(),
            timeProvider = FakeTimeProvider(),
        )
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase("l1")).error)
    }

    @Test
    fun setDefaultPromotesLedgerAndUnarchivesIt() = runTest {
        val ledgers = FakeLedgerRepository(
            mutableListOf(
                ledger("l1", "默认账本", isDefault = true),
                ledger("l2", "旅行", archivedAt = 10L),
            ),
        )
        val useCase = SetDefaultLedgerUseCase(ledgers, FakeTransactionRunner(), FakeTimeProvider())

        assertIs<AppResult.Success<Unit>>(useCase("l2"))
        assertTrue(ledgers.getById("l2")!!.isDefault)
        assertFalse(ledgers.getById("l2")!!.isArchived)
        assertFalse(ledgers.getById("l1")!!.isDefault)
    }

    @Test
    fun currentLedgerFallsBackToDefaultWhenSelectionIsMissing() = runTest {
        val ledgers = FakeLedgerRepository(
            mutableListOf(ledger("l1", "默认账本", isDefault = true), ledger("l2", "旅行")),
        )
        val store = FakeCurrentLedgerStore("missing-id")
        val useCase = ObserveCurrentLedgerUseCase(store, ledgers)

        assertEquals("l1", useCase().first()?.id)

        val select = SelectLedgerUseCase(store, ledgers)
        assertIs<AppResult.Success<Unit>>(select("l2"))
        assertEquals("l2", store.current)
        assertEquals("l2", useCase().first()?.id)
    }

    @Test
    fun selectRejectsArchivedLedger() = runTest {
        val ledgers = FakeLedgerRepository(mutableListOf(ledger("l1", "默认账本", isDefault = true, archivedAt = 1L)))
        val store = FakeCurrentLedgerStore()
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(SelectLedgerUseCase(store, ledgers)("l1")).error)
    }

    private fun ledger(
        id: String,
        name: String,
        isDefault: Boolean = false,
        archivedAt: Long? = null,
    ) = Ledger(
        id = id,
        name = name,
        isDefault = isDefault,
        archivedAt = archivedAt,
        createdAt = 0,
        updatedAt = 0,
    )
}