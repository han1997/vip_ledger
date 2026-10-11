package com.han1997.vipledger.core.data.ai

import android.speech.SpeechRecognizer
import com.han1997.vipledger.core.domain.ai.VoiceFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SpeechRecognizer 错误码 → [VoiceFailure] 分类映射（PRD「语音」切片失败分类）。
 *
 * 用 Robolectric 才能引用 `android.speech.SpeechRecognizer.ERROR_*` 常量。
 */
@RunWith(RobolectricTestRunner::class)
class SystemSpeechTranscriberTest {

    @Test
    fun noMatchMapsToNoResult() {
        assertEquals(VoiceFailure.NO_RESULT, mapSpeechError(SpeechRecognizer.ERROR_NO_MATCH))
    }

    @Test
    fun speechTimeOutMapsToTimeout() {
        assertEquals(VoiceFailure.TIMEOUT, mapSpeechError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun insufficientPermissionsMapsToPermissionDenied() {
        assertEquals(
            VoiceFailure.PERMISSION_DENIED,
            mapSpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
    }

    @Test
    fun networkErrorsMapToNoService() {
        assertEquals(VoiceFailure.NO_SERVICE, mapSpeechError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(VoiceFailure.NO_SERVICE, mapSpeechError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertEquals(VoiceFailure.NO_SERVICE, mapSpeechError(SpeechRecognizer.ERROR_SERVER))
        assertEquals(VoiceFailure.NO_SERVICE, mapSpeechError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
    }

    @Test
    fun otherErrorsMapToUnknown() {
        assertEquals(VoiceFailure.UNKNOWN, mapSpeechError(SpeechRecognizer.ERROR_AUDIO))
        assertEquals(VoiceFailure.UNKNOWN, mapSpeechError(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(VoiceFailure.UNKNOWN, mapSpeechError(12345))
    }
}