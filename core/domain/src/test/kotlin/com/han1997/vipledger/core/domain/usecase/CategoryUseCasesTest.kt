package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeCategoryRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.FakeTransactionRunner
import com.han1997.vipledger.core.domain.fake.FakeTxnRepository
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.domain.usecase.category.DeleteCategoryUseCase
import com.han1997.vipledger.core.domain.usecase.category.SaveCategoryUseCase
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CategoryUseCasesTest {

    private fun saveUseCase(categories: FakeCategoryRepository) =
        SaveCategoryUseCase(categories, FakeTimeProvider(), SequentialIdGenerator())

    @Test
    fun createTopLevelAndChildKeepsSortOrder() = runTest {
        val categories = FakeCategoryRepository()
        val useCase = saveUseCase(categories)

        val food = assertIs<AppResult.Success<Category>>(
            useCase(null, "l1", null, "餐饮", CategoryKind.EXPENSE),
        ).value
        val transport = assertIs<AppResult.Success<Category>>(
            useCase(null, "l1", null, "交通", CategoryKind.EXPENSE),
        ).value
        val lunch = assertIs<AppResult.Success<Category>>(
            useCase(null, "l1", food.id, "午餐", CategoryKind.EXPENSE),
        ).value

        assertEquals(0, food.sortOrder)
        assertEquals(1, transport.sortOrder)
        assertEquals(food.id, lunch.parentId)
        assertEquals(0, lunch.sortOrder)
    }

    @Test
    fun rejectDuplicateNameUnderSameParentAndThirdLevel() = runTest {
        val food = category("c1", "餐饮", parentId = null)
        val lunch = category("c2", "午餐", parentId = "c1")
        val categories = FakeCategoryRepository(mutableListOf(food, lunch))
        val useCase = saveUseCase(categories)

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(null, "l1", "c1", "午餐", CategoryKind.EXPENSE)).error)
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(null, "l1", "c2", "工作餐", CategoryKind.EXPENSE)).error)
    }

    @Test
    fun rejectKindMismatchWithParent() = runTest {
        val categories = FakeCategoryRepository(mutableListOf(category("c1", "餐饮", parentId = null)))
        val error = assertIs<AppResult.Failure>(
            saveUseCase(categories).invoke(null, "l1", "c1", "兼职", CategoryKind.INCOME),
        ).error
        assertIs<AppError.Validation>(error)
    }

    @Test
    fun deleteRejectsCategoryUsedByTxn() = runTest {
        val categories = FakeCategoryRepository(mutableListOf(category("c1", "餐饮", parentId = null)))
        val txns = FakeTxnRepository(
            mutableListOf(
                Txn(
                    id = "t1",
                    ledgerId = "l1",
                    type = TransactionType.EXPENSE,
                    amount = Money.ofCents(100),
                    accountId = "a1",
                    categoryId = "c1",
                    occurredAt = 0,
                    createdAt = 0,
                    updatedAt = 0,
                ),
            ),
        )
        val useCase = DeleteCategoryUseCase(categories, txns, FakeTransactionRunner(), FakeTimeProvider())

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase("c1")).error)
    }

    @Test
    fun deleteCascadesToChildrenAndIsTransactional() = runTest {
        val categories = FakeCategoryRepository(
            mutableListOf(
                category("c1", "餐饮", parentId = null),
                category("c2", "午餐", parentId = "c1"),
                category("c3", "早餐", parentId = "c1"),
            ),
        )
        val runner = FakeTransactionRunner()
        val useCase = DeleteCategoryUseCase(categories, FakeTxnRepository(), runner, FakeTimeProvider())

        val deleted = assertIs<AppResult.Success<Int>>(useCase("c1")).value
        assertEquals(3, deleted)
        assertEquals(1, runner.transactionCount)
        assertTrue(categories.snapshot().all { it.deletedAt != null })
    }

    @Test
    fun deleteMissingCategoryReturnsNotFound() = runTest {
        val useCase = DeleteCategoryUseCase(
            FakeCategoryRepository(),
            FakeTxnRepository(),
            FakeTransactionRunner(),
            FakeTimeProvider(),
        )
        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(useCase("nope")).error)
    }

    private fun category(id: String, name: String, parentId: String?) = Category(
        id = id,
        ledgerId = "l1",
        parentId = parentId,
        name = name,
        kind = CategoryKind.EXPENSE,
        createdAt = 0,
        updatedAt = 0,
    )
}