package com.han1997.vipledger.core.domain.support

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

/**
 * 组合式用例的写入骨架：把校验失败、溢出与底层异常统一转成 [AppResult]，
 * 同时在事务里用异常中止触发回滚（见 savings/lending/reimbursement 用例）。
 */
internal class WriteFailure(val error: AppError) : RuntimeException()

internal fun <T> AppResult<T>.orRollback(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw WriteFailure(error)
}

internal suspend fun <T> checkedWrite(block: suspend () -> T): AppResult<T> = try {
    AppResult.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failed: WriteFailure) {
    AppResult.failure(failed.error)
} catch (bad: IllegalArgumentException) {
    AppResult.failure(AppError.Validation(bad.message, bad))
} catch (overflow: ArithmeticException) {
    AppResult.failure(AppError.Validation("金额超出支持范围", overflow))
} catch (error: Exception) {
    AppResult.failure(AppError.Storage("保存失败", error))
}
