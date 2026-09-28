package com.han1997.vipledger.core.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class EncryptedSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("backup_settings", Context.MODE_PRIVATE)
    private val alias = "vipledger.backup.settings"

    fun read(): BackupSettings {
        val encoded = prefs.getString("payload", null) ?: return BackupSettings()
        return runCatching {
            val data = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8))
            BackupSettings(
                endpoint = json.optString("endpoint"), path = json.optString("path", "vipledger"),
                username = json.optString("username"), password = json.optString("password"),
                passphrase = json.optString("passphrase"), encryptionEnabled = json.optBoolean("encryptionEnabled"),
                scheduled = json.optBoolean("scheduled"), wifiOnly = json.optBoolean("wifiOnly", true),
                retention = json.optInt("retention", 10),
            )
        }.getOrDefault(BackupSettings())
    }

    fun write(settings: BackupSettings) {
        val json = JSONObject()
            .put("endpoint", settings.endpoint)
            .put("path", settings.path)
            .put("username", settings.username)
            .put("password", settings.password)
            .put("passphrase", settings.passphrase)
            .put("encryptionEnabled", settings.encryptionEnabled)
            .put("scheduled", settings.scheduled)
            .put("wifiOnly", settings.wifiOnly)
            .put("retention", settings.retention)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("payload", android.util.Base64.encodeToString(sealed, android.util.Base64.NO_WRAP)).commit())
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }
}
