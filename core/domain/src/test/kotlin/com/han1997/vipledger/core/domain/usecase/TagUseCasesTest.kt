package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.fake.FakeTagRepository
import com.han1997.vipledger.core.domain.fake.FakeTimeProvider
import com.han1997.vipledger.core.domain.fake.SequentialIdGenerator
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.domain.usecase.tag.DeleteTagUseCase
import com.han1997.vipledger.core.domain.usecase.tag.SaveTagUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TagUseCasesTest {

    private val tags = FakeTagRepository()

    private fun saveUseCase() = SaveTagUseCase(tags, FakeTimeProvider(), SequentialIdGenerator())

    @Test
    fun createTrimsNameAndAssignsSortOrder() = runTest {
        val useCase = saveUseCase()
        val first = assertIs<AppResult.Success<Tag>>(useCase(null, "l1", " 报销 ")).value
        val second = assertIs<AppResult.Success<Tag>>(useCase(null, "l1", "旅行")).value

        assertEquals("报销", first.name)
        assertEquals(0, first.sortOrder)
        assertEquals(1, second.sortOrder)
    }

    @Test
    fun rejectBlankAndDuplicatedNames() = runTest {
        val useCase = saveUseCase()
        useCase(null, "l1", "报销")

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(null, "l1", "   ")).error)
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(useCase(null, "l1", "报销")).error)
    }

    @Test
    fun deleteSoftDeletesAndMissingReturnsNotFound() = runTest {
        val useCase = saveUseCase()
        val tag = assertIs<AppResult.Success<Tag>>(useCase(null, "l1", "报销")).value
        val deleteUseCase = DeleteTagUseCase(tags, FakeTimeProvider())

        assertIs<AppResult.Success<Unit>>(deleteUseCase(tag.id))
        assertTrue(tags.snapshot().single().deletedAt != null)
        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(deleteUseCase("nope")).error)
    }
}