package com.han1997.vipledger.core.domain.ai

/**
 * 语音输入界面相位：纯 Kotlin 状态机（PRD「语音」切片）。
 *
 * - [IDLE]：未在录音（初始 / 录音结束 / 取消 / 重试前）。
 * - [LISTENING]：按住录音中，实时展示 [VoiceInputState.partialText]。
 * - [FAILED]：本次识别失败（[VoiceInputState.failure] 给出分类），提示可重试或改手工输入。
 */
enum class VoicePhase { IDLE, LISTENING, FAILED }

/**
 * 语音输入状态机（纯 Kotlin，无 Android 依赖，JVM 可测）。
 *
 * 事件驱动：`start → ready/partial → succeeded | failed | cancel → reset`。
 * 取消/离页进入 [VoicePhase.IDLE] 且**不带走文本**（由事件语义保证：取消不产生 onResult，
 * 文本提交只发生在成功回调里，见 `AiEntryViewModel`）。
 */
data class VoiceInputState(
    val phase: VoicePhase = VoicePhase.IDLE,
    /** 录音中的实时转写（仅 [VoicePhase.LISTENING] 期间更新）。 */
    val partialText: String = "",
    /** 失败分类，仅 [VoicePhase.FAILED] 期间非空。 */
    val failure: VoiceFailure? = null,
    /** 本次识别的模式（端侧 / 可能联网），启动成功后由 [VoiceEvent.Ready] 写入。 */
    val mode: VoiceRecognitionMode? = null,
) {
    val isListening: Boolean get() = phase == VoicePhase.LISTENING
    val isFailed: Boolean get() = phase == VoicePhase.FAILED
}

sealed interface VoiceEvent {

    /** 用户按住启动：进入 [VoicePhase.LISTENING]，清空上一轮的失败与文本。 */
    data object Start : VoiceEvent

    /** 识别启动成功，带实际模式（界面据此标注「可能联网」）。 */
    data class Ready(val mode: VoiceRecognitionMode) : VoiceEvent

    /** 实时转写更新。 */
    data class Partial(val text: String) : VoiceEvent

    /** 识别成功：回到 [VoicePhase.IDLE]。文本由外层（`AiEntryViewModel`）提交到输入框。 */
    data object Succeeded : VoiceEvent

    /** 识别失败：进入 [VoicePhase.FAILED] 并记录分类。 */
    data class Failed(val failure: VoiceFailure) : VoiceEvent

    /** 取消 / 离页：回到 [VoicePhase.IDLE]，不提交文本。 */
    data object Cancel : VoiceEvent

    /** 复位（重试前 / 离页）：回到初始态，丢弃失败信息。 */
    data object Reset : VoiceEvent
}

fun VoiceInputState.reduce(event: VoiceEvent): VoiceInputState = when (event) {
    VoiceEvent.Start -> VoiceInputState(phase = VoicePhase.LISTENING)
    is VoiceEvent.Ready -> copy(mode = event.mode)
    is VoiceEvent.Partial -> if (phase == VoicePhase.LISTENING) copy(partialText = event.text) else this
    VoiceEvent.Succeeded -> VoiceInputState()
    is VoiceEvent.Failed -> VoiceInputState(VoicePhase.FAILED, failure = event.failure)
    VoiceEvent.Cancel -> VoiceInputState()
    VoiceEvent.Reset -> VoiceInputState()
}