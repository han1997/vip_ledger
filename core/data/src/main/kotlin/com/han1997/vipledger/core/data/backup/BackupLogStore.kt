package com.han1997.vipledger.core.data.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一次自动备份的执行结果（设置页可见的备份日志）。 */
data class BackupLogEntry(
    val success: Boolean,
    val time: Long,
    val message: String,
)

/**
 * 自动备份执行日志的有界存储。
 *
 * 只保留最近 [LIMIT] 条，且**只存结果与时间**——异常信息来自 [WebDavClient]，
 * 本身不含凭据/口令/账单内容（见 `.trellis/spec/android/logging-guidelines.md` 红线）。
 */
class BackupLogStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun append(entry: BackupLogEntry) {
        val array = readArray()
        array.put(
            JSONObject()
                .put("success", entry.success)
                .put("time", entry.time)
                .put("message", entry.message),
        )
        val trimmed = JSONArray()
        val start = (array.length() - LIMIT).coerceAtLeast(0)
        for (index in start until array.length()) trimmed.put(array.get(index))
        prefs.edit().putString(KEY_ENTRIES, trimmed.toString()).apply()
    }

    /** 最新在前。 */
    fun read(): List<BackupLogEntry> {
        val array = readArray()
        return (array.length() - 1 downTo 0).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            BackupLogEntry(
                success = item.optBoolean("success"),
                time = item.optLong("time"),
                message = item.optString("message"),
            )
        }
    }

    private fun readArray(): JSONArray =
        runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())

    companion object {
        const val PREFS_NAME = "backup_execution_log"
        const val KEY_ENTRIES = "entries"
        const val LIMIT = 20
    }
}
