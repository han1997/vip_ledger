package com.han1997.vipledger.core.domain.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PRD Requirement 5 / `.trellis/spec/android/logging-guidelines.md`：配置结构打印时不得泄漏敏感字段。 */
class AiSettingsTest {

    @Test
    fun toStringNeverLeaksApiKey() {
        val text = AiSettings(baseUrl = "https://api.example.com/v1", apiKey = "sk-super-secret").toString()
        assertFalse(text.contains("sk-super-secret"), "apiKey 明文不得出现在 toString：$text")
        assertTrue(text.contains("***"))
    }

    @Test
    fun toStringRedactsUrlUserInfo() {
        val text = AiSettings(baseUrl = "https://user:pass@api.example.com/v1", apiKey = "").toString()
        assertFalse(text.contains("user:pass"), "URL 内嵌凭据不得出现在 toString：$text")
        assertTrue(text.contains("https://api.example.com/v1"))
    }

    @Test
    fun toStringKeepsHarmlessFields() {
        val settings = AiSettings(
            baseUrl = "http://192.168.10.4:11434/v1",
            apiKey = "",
            model = "gemma3:4b",
            networkEnabled = true,
            autoTrainEnabled = true,
        )
        assertEquals(
            "AiSettings(baseUrl=http://192.168.10.4:11434/v1, apiKey=, model=gemma3:4b, " +
                "networkEnabled=true, autoTrainEnabled=true, voiceOnlineTranscription=false)",
            settings.toString(),
        )
    }

    @Test
    fun autoTrainDefaultsOff() {
        // M7 验收：「自动训练」开关默认关闭，关闭时训练触发点不得发生任何训练。
        assertFalse(AiSettings().autoTrainEnabled, "自动训练必须默认关闭")
    }

    @Test
    fun voiceOnlineTranscriptionDefaultsOffAndIsIndependent() {
        // PRD「语音」切片：联网转写独立开关默认关；与云端解析开关互相独立。
        val defaults = AiSettings()
        assertFalse(defaults.voiceOnlineTranscription, "语音联网转写必须默认关闭")
        val enabled = defaults.copy(voiceOnlineTranscription = true)
        assertTrue(enabled.voiceOnlineTranscription)
        assertFalse(enabled.networkEnabled, "语音联网转写开关不应联动云端解析开关")
    }
}
