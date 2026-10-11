package com.han1997.vipledger.core.domain.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 语音输入状态机（PRD「语音」切片）的纯逻辑测试。
 *
 * 覆盖：按住开始、松开结束、上滑取消、失败可重试、离页复位；
 * 以及「取消/离页不提交文本」（取消事件只复位状态，文本提交只发生在成功回调，不由此状态机承担）。
 */
class VoiceInputStateTest {

    @Test
    fun startEntersListeningAndClearsPreviousState() {
        val failed = VoiceInputState(phase = VoicePhase.FAILED, failure = VoiceFailure.TIMEOUT)
        val listening = failed.reduce(VoiceEvent.Start)
        assertEquals(VoicePhase.LISTENING, listening.phase)
        assertTrue(listening.isListening)
        assertNull(listening.failure, "重新开始时清空上一轮失败信息")
        assertEquals("", listening.partialText)
    }

    @Test
    fun readyCarriesRecognitionMode() {
        val state = VoiceInputState(phase = VoicePhase.LISTENING)
        val ready = state.reduce(VoiceEvent.Ready(VoiceRecognitionMode.MAYBE_ONLINE))
        assertEquals(VoiceRecognitionMode.MAYBE_ONLINE, ready.mode)
        val onDevice = state.reduce(VoiceEvent.Ready(VoiceRecognitionMode.ON_DEVICE))
        assertEquals(VoiceRecognitionMode.ON_DEVICE, onDevice.mode)
    }

    @Test
    fun partialOnlyUpdatesWhileListening() {
        val idle = VoiceInputState().reduce(VoiceEvent.Partial("午饭"))
        assertEquals("", idle.partialText, "非聆听相位忽略转写文本")
        val failed = VoiceInputState(phase = VoicePhase.FAILED).reduce(VoiceEvent.Partial("午饭"))
        assertEquals("", failed.partialText)
        val listening = VoiceInputState(phase = VoicePhase.LISTENING)
            .reduce(VoiceEvent.Partial("午饭"))
        assertEquals("午饭", listening.partialText)
        val updated = listening.reduce(VoiceEvent.Partial("午饭 35"))
        assertEquals("午饭 35", updated.partialText)
    }

    @Test
    fun succeededResetsToIdle() {
        val listening = VoiceInputState(
            phase = VoicePhase.LISTENING,
            partialText = "午饭 35",
            mode = VoiceRecognitionMode.ON_DEVICE,
        )
        val done = listening.reduce(VoiceEvent.Succeeded)
        assertEquals(VoicePhase.IDLE, done.phase)
        assertEquals("", done.partialText)
        assertNull(done.failure)
        assertNull(done.mode)
    }

    @Test
    fun failedKeepsFailureAndAllowsRetry() {
        // 失败 → FAILED 相位 + 记录分类。
        val failed = VoiceInputState(phase = VoicePhase.LISTENING)
            .reduce(VoiceEvent.Failed(VoiceFailure.NO_SERVICE))
        assertEquals(VoicePhase.FAILED, failed.phase)
        assertTrue(failed.isFailed)
        assertEquals(VoiceFailure.NO_SERVICE, failed.failure)

        // 重试 → 回到 IDLE 等待再次按住（重新 Start 进入聆听）。
        val retried = failed.reduce(VoiceEvent.Reset)
        assertEquals(VoicePhase.IDLE, retried.phase)
        assertNull(retried.failure)
    }

    @Test
    fun cancelResetsToIdleWithoutCarryingText() {
        val listening = VoiceInputState(
            phase = VoicePhase.LISTENING,
            partialText = "午饭",
            mode = VoiceRecognitionMode.MAYBE_ONLINE,
        )
        val cancelled = listening.reduce(VoiceEvent.Cancel)
        assertEquals(VoicePhase.IDLE, cancelled.phase)
        assertEquals("", cancelled.partialText, "取消不带走文本")
        assertNull(cancelled.mode)
    }

    @Test
    fun resetFromListeningIsIdempotentIdle() {
        val listening = VoiceInputState(phase = VoicePhase.LISTENING, partialText = "午饭")
        val reset = listening.reduce(VoiceEvent.Reset)
        assertEquals(VoicePhase.IDLE, reset.phase)
        assertEquals("", reset.partialText)
    }
}

/** 识别模式判定（PRD「语音」切片硬约束）：端侧可用恒用端侧；关闭联网只用端侧；否则提示手工。 */
class VoiceRecognitionModeTest {

    @Test
    fun onDeviceAvailableAlwaysOnDevice() {
        assertEquals(
            VoiceRecognitionMode.ON_DEVICE,
            resolveVoiceRecognitionMode(onDeviceAvailable = true, onlineTranscriptionEnabled = false),
        )
        assertEquals(
            VoiceRecognitionMode.ON_DEVICE,
            resolveVoiceRecognitionMode(onDeviceAvailable = true, onlineTranscriptionEnabled = true),
        )
    }

    @Test
    fun noOnDeviceWithOnlineEnabledFallsBackToMaybeOnline() {
        assertEquals(
            VoiceRecognitionMode.MAYBE_ONLINE,
            resolveVoiceRecognitionMode(onDeviceAvailable = false, onlineTranscriptionEnabled = true),
        )
    }

    @Test
    fun noOnDeviceWithOnlineDisabledIsUnavailable() {
        assertNull(
            resolveVoiceRecognitionMode(onDeviceAvailable = false, onlineTranscriptionEnabled = false),
            "端侧不可用且联网转写关闭时不得启动识别，应提示改手工输入",
        )
    }
}