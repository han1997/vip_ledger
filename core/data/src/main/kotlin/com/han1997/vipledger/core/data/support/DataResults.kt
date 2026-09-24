package com.han1997.vipledger.core.data.support

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import java.io.IOException

/**
 * 把数据层的底层异常翻译成 [AppError]（见 `.trellis/spec/android/error-handling.md`）。
 * 读接口返回 Flow、异常在收集时抛出，因此这里只包写入路径。
 */
internal inline fun <T> dataResult(block: () -> T): AppResult<T> = try {
    AppResult.success(block())
} catch (error: SQLiteConstraintException) {
    AppResult.failure(AppError.Conflict(error.message, error))
} catch (error: SQLiteException) {
    AppResult.failure(AppError.Storage(error.message, error))
} catch (error: IOException) {
    AppResult.failure(AppError.Storage(error.message, error))
} catch (error: IllegalArgumentException) {
    AppResult.failure(AppError.Parse(error.message, error))
} catch (error: IllegalStateException) {
    AppResult.failure(AppError.Unknown(error.message, error))
}
