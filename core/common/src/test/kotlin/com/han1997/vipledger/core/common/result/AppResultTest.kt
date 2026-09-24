package com.han1997.vipledger.core.common.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppResultTest {

    @Test
    fun mapTransformsSuccessOnly() {
        val success: AppResult<Int> = AppResult.success(2)
        assertEquals(AppResult.success(4), success.map { it * 2 })

        val failure: AppResult<Int> = AppResult.failure(AppError.Network("断网"))
        assertTrue(failure.map { it * 2 } is AppResult.Failure)
    }

    @Test
    fun flatMapChainsResults() {
        val result = AppResult.success(2).flatMap { value -> AppResult.success(value + 1) }
        assertEquals(3, result.getOrNull())

        val chainedFailure = AppResult.success(2).flatMap { AppResult.failure(AppError.Conflict()) }
        assertTrue(chainedFailure.isFailure)
    }

    @Test
    fun sideEffectsAreScopedToMatchingBranch() {
        var successCalls = 0
        var failureCalls = 0

        AppResult.success("ok")
            .onSuccess { successCalls++ }
            .onFailure { failureCalls++ }

        assertEquals(1, successCalls)
        assertEquals(0, failureCalls)
    }

    @Test
    fun accessorsBehaveAsDocumented() {
        val failure: AppResult<Int> = AppResult.failure(AppError.Parse("bad json"))
        assertNull(failure.getOrNull())
        assertEquals(7, failure.getOrDefault(7))
        assertTrue(failure.isFailure)
        assertEquals(false, failure.isSuccess)
    }
}
