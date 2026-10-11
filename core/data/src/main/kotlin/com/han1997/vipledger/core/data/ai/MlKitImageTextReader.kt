package com.han1997.vipledger.core.data.ai

import android.net.Uri
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.data.autobook.PaymentOcr
import com.han1997.vipledger.core.domain.ai.ImageTextReader
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ImageTextReader] 的 ML Kit 实现（P5「图片与系统分享」切片）。
 *
 * 复用 [PaymentOcr] 的本机中文识别（打包模型、不联网、不落盘）；主动选图与系统分享的
 * 图片在识别后即丢弃，原图默认不保存（PRD）。失败归类：
 * - 图片不可读 / 已失效（`IOException`、`SecurityException`）→ `AppError.Storage`
 * - 识别引擎失败或未识别出任何文本 → `AppError.Parse`
 */
@Singleton
class MlKitImageTextReader @Inject constructor(
    private val ocr: PaymentOcr,
) : ImageTextReader {

    override suspend fun readText(imageUri: String): AppResult<String> {
        val uri = runCatching { Uri.parse(imageUri) }.getOrNull()
            ?: return AppResult.failure(AppError.Storage("无法读取所选图片"))
        return runCatching { ocr.recognizeUri(uri) }.fold(
            onSuccess = { text ->
                if (text.isBlank()) {
                    AppResult.failure(AppError.Parse("未能从图片中识别出文本"))
                } else {
                    AppResult.success(text)
                }
            },
            onFailure = { e ->
                val error = if (e is IOException || e is SecurityException) {
                    AppError.Storage("无法读取所选图片", e)
                } else {
                    AppError.Parse("图片文本识别失败", e)
                }
                AppResult.failure(error)
            },
        )
    }
}