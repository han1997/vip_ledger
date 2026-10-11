package com.han1997.vipledger.core.data.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import com.han1997.vipledger.core.domain.ai.SpeechTranscriber
import com.han1997.vipledger.core.domain.ai.VoiceFailure
import com.han1997.vipledger.core.domain.ai.VoiceRecognitionMode
import com.han1997.vipledger.core.domain.ai.resolveVoiceRecognitionMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于系统 [SpeechRecognizer] 的语音转写实现（PRD「语音」切片）。
 *
 * - 端侧优先：API 31+ 且 `isOnDeviceRecognitionAvailable` 时用 `createOnDeviceSpeechRecognizer`。
 * - 端侧不可用且 `AiSettings.voiceOnlineTranscription` 开启时退化为普通识别：
 *   **界面必须标注「可能联网」**（`onReady` 回调携带 [VoiceRecognitionMode.MAYBE_ONLINE]），绝不冒称离线。
 * - 端侧不可用且开关关闭 → 不启动，直接 `onError(NO_SERVICE)`（提示改手工输入）。
 *
 * 所有操作切到主线程执行（`SpeechRecognizer` 要求主线程 Looper）；原始音频不落盘（PRD）。
 */
@Singleton
class SystemSpeechTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: AiSettingsRepository,
) : SpeechTranscriber {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechTranscriber.Listener? = null

    override fun setListener(listener: SpeechTranscriber.Listener?) {
        this.listener = listener
    }

    override fun start() {
        mainHandler.post {
            val current = listener ?: return@post
            val canRecord = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (!canRecord) {
                current.onError(VoiceFailure.PERMISSION_DENIED)
                return@post
            }

            val mode = resolveVoiceRecognitionMode(
                onDeviceAvailable = onDeviceRecognitionAvailable(),
                onlineTranscriptionEnabled = settingsRepository.settings().voiceOnlineTranscription,
            ) ?: run {
                current.onError(VoiceFailure.NO_SERVICE)
                return@post
            }

            val recognizer = try {
                recognizer ?: createRecognizer(mode).also { recognizer = it }
            } catch (e: RuntimeException) {
                current.onError(VoiceFailure.NO_SERVICE)
                return@post
            }
            recognizer.setRecognitionListener(androidListener)
            try {
                recognizer.startListening(recognizerIntent())
            } catch (e: SecurityException) {
                current.onError(VoiceFailure.PERMISSION_DENIED)
                return@post
            } catch (e: IllegalStateException) {
                current.onError(VoiceFailure.NO_SERVICE)
                return@post
            }
            current.onReady(mode)
        }
    }

    override fun stop() {
        mainHandler.post { recognizer?.stopListening() }
    }

    override fun cancel() {
        mainHandler.post {
            recognizer?.cancel()
            releaseRecognizer()
        }
    }

    /** 每次会话结束或失败都释放；下次 start 按最新设备能力与设置重新创建，避免模式标签漂移。 */
    private fun releaseRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun onDeviceRecognitionAvailable(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        } else {
            false
        }

    private fun createRecognizer(mode: VoiceRecognitionMode): SpeechRecognizer =
        if (mode == VoiceRecognitionMode.ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 端侧识别器仅在 API 31+（[onDeviceRecognitionAvailable] 已保证）时走到这个分支。
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private val androidListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            val current = listener
            releaseRecognizer()
            current?.onError(mapSpeechError(error))
        }

        override fun onResults(results: Bundle?) {
            val text = bestResultText(results)
            val current = listener
            releaseRecognizer()
            if (text.isNullOrBlank()) {
                current?.onError(VoiceFailure.NO_RESULT)
            } else {
                current?.onResult(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = bestResultText(partialResults)
            if (!text.isNullOrBlank()) {
                listener?.onPartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun bestResultText(bundle: Bundle?): String? {
        if (bundle == null) return null
        val results = bundle.getCharSequenceArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?: return null
        return results.firstOrNull()?.toString()?.trim()
    }
}

/** SpeechRecognizer 错误码 → [VoiceFailure] 分类（PRD 失败分类：无服务/权限/超时/无结果/未知）。 */
fun mapSpeechError(error: Int): VoiceFailure = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH -> VoiceFailure.NO_RESULT
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceFailure.TIMEOUT
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceFailure.PERMISSION_DENIED
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    -> VoiceFailure.NO_SERVICE
    else -> VoiceFailure.UNKNOWN
}