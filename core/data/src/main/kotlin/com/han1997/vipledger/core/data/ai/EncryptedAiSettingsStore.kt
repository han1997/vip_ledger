package com.han1997.vipledger.core.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.han1997.vipledger.core.domain.ai.AiSettings
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AI 配置的加密存储，与 M5 的 `EncryptedSettingsStore`（备份）同构：
 * AndroidKeyStore + AES/GCM，密文（IV + payload）Base64 后写入独立的 `shared_prefs/ai_settings.xml`。
 *
 * 独立的 prefs 文件名与密钥别名，确保**不影响** M5 的备份配置（PRD D4 / Requirement 5）；
 * `apiKey` 与其他字段一并加密，磁盘上不出现明文。
 */
internal class EncryptedAiSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val alias = "vipledger.ai.settings"

    fun read(): AiSettings {
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return AiSettings()
        return runCatching {
            val data = android.util.Base64.decode(payload, android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, IV_SIZE)))
            val json = JSONObject(String(cipher.doFinal(data.copyOfRange(IV_SIZE, data.size)), Charsets.UTF_8))
            AiSettings(
                baseUrl = json.optString("baseUrl"),
                apiKey = json.optString("apiKey"),
                model = json.optString("model", AiSettings.DEFAULT_MODEL),
                networkEnabled = json.optBoolean("networkEnabled", false),
                autoTrainEnabled = json.optBoolean("autoTrainEnabled", false),
            )
        }.getOrDefault(AiSettings())
    }

    fun write(settings: AiSettings) {
        val json = JSONObject()
            .put("baseUrl", settings.baseUrl)
            .put("apiKey", settings.apiKey)
            .put("model", settings.model)
            .put("networkEnabled", settings.networkEnabled)
            .put("autoTrainEnabled", settings.autoTrainEnabled)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(
            prefs.edit()
                .putString(KEY_PAYLOAD, android.util.Base64.encodeToString(sealed, android.util.Base64.NO_WRAP))
                .commit(),
        )
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_PAYLOAD = "payload"
        const val IV_SIZE = 12
    }
}
