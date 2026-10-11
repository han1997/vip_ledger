package com.han1997.vipledger.core.domain.ai

import com.han1997.vipledger.core.common.result.AppResult

/**
 * 图片文本识别接缝（P5「图片与系统分享」切片）：本机中文 OCR（ML Kit，打包模型，不联网）。
 *
 * 与 [SpeechTranscriber] 同构：接口在 `:core:domain`，Android 实现放 `:core:data`，
 * `:feature:ai` 只注入接口。URI 以字符串传入，保持 domain 纯 Kotlin（无 Android 依赖）。
 *
 * OCR 文本不落库、不进日志（复用 auto-bookkeeping 隐私约定）；图片原文默认不保存。
 */
interface ImageTextReader {

    /**
     * 读取一张图片中的全部文本。
     *
     * @param imageUri `content://` Uri 字符串（Photo Picker / 系统分享授权的读取权限）。
     * @return 识别出的全文；图片不可读、识别失败或未识别出任何文本都返回 `AppResult.Failure`，
     *   不抛异常到调用方（ai-ml.md：任何一级都不得把异常抛给 UI）。
     */
    suspend fun readText(imageUri: String): AppResult<String>
}