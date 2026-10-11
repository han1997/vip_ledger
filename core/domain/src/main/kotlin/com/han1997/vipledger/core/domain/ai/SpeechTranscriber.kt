package com.han1997.vipledger.core.domain.ai

/**
 * 语音转写失败分类（PRD「语音」切片：无服务 / 权限被拒 / 超时 / 无结果 / 未知）。
 *
 * UI 必须按类别给用户可操作的提示（可改手工输入 / 重试 / 去开权限），不静默失败。
 */
enum class VoiceFailure {
    /** 无可用语音识别服务：端侧不可用且联网转写关闭、系统无可识别器、识别器繁忙或联网失败。 */
    NO_SERVICE,

    /** `RECORD_AUDIO` 权限被拒。 */
    PERMISSION_DENIED,

    /** 超时（长时间没听到有效语音）。 */
    TIMEOUT,

    /** 有录音但没有识别出任何文本。 */
    NO_RESULT,

    /** 其余未知错误。 */
    UNKNOWN,
}

/**
 * 语音识别模式。
 *
 * - [ON_DEVICE]：系统端侧识别，完全不联网。
 * - [MAYBE_ONLINE]：退化为普通系统识别（可能联网，见 PRD：绝不能把普通服务冒称离线）。
 */
enum class VoiceRecognitionMode { ON_DEVICE, MAYBE_ONLINE }

/**
 * 仅根据「设备能力 + 联网转写开关」判定识别模式（PRD 硬约束）。
 *
 * - 端侧可用 → 恒用端侧（无关开关）。
 * - 端侧不可用且开关开启 → 退化为普通识别（界面必须标注「可能联网」）。
 * - 端侧不可用且开关关闭 → 返回 null：**不启动**，提示改手工输入。
 *
 * 纯函数，便于 JVM 单测钉住「关闭时只用端侧、端侧不可用即提示改手工」。
 */
fun resolveVoiceRecognitionMode(
    onDeviceAvailable: Boolean,
    onlineTranscriptionEnabled: Boolean,
): VoiceRecognitionMode? = when {
    onDeviceAvailable -> VoiceRecognitionMode.ON_DEVICE
    onlineTranscriptionEnabled -> VoiceRecognitionMode.MAYBE_ONLINE
    else -> null
}

/**
 * 语音转写接缝（PRD「语音」切片）：只暴露 开始/停止/取消 与回调，不暴露底层
 * `SpeechRecognizer` 细节。接口在 `:core:domain`，Android 实现放 `:core:data`
 * （与 `LlmParserClient` / `AiSettingsRepository` 同构，见 architecture.md「接口向内、实现向外」）。
 */
interface SpeechTranscriber {

    fun start()

    /** 结束并取最终结果（回调 [Listener.onResult]）。 */
    fun stop()

    /** 取消，丢弃本次结果并释放底层识别器（不回调 onResult）。 */
    fun cancel()

    fun setListener(listener: Listener?)

    interface Listener {
        /**
         * 识别启动成功后回调，带本次实际模式（端侧 / 可能联网）。
         * 界面据此标注「系统识别可能联网」，绝不冒称离线（PRD 硬约束）。
         */
        fun onReady(mode: VoiceRecognitionMode)

        fun onPartialResult(text: String)

        fun onResult(text: String)

        fun onError(failure: VoiceFailure)
    }
}