package com.han1997.vipledger.core.data.backup

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 表数据的 JSON 编解码器：把整库按 `docs/DATA-MODEL.md` 的表/列名导成
 * `{"tables": { "<表名>": [ { "<列名>": 值 }, ... ] }}`，反向可无损写回。
 *
 * **两处用途共用同一份编解码**（这是刻意的决定，不是巧合）：
 * - 快照备份的 `db.json`（再经 manifest 计数/校验和包裹，可选加密）；
 * - 设置页「导出 JSON」给用户的迁移文件（就是这份 JSON 本身）。
 *
 * 之所以不复用领域模型序列化：下行（恢复）需要逐表逐列写回 SQLite，
 * 表名/列名与 DATA-MODEL 完全一致，直接编解码原始行最稳、最便于跨版本迁移。
 * 快照的 manifest 与加密是**另一层**包装，不能省略成同一件事。
 *
 * BLOB 列在 JSON 里用无换行 Base64 文本承载（JSON 没有二进制），写回时按
 * `PRAGMA table_info` 判定列类型再解码，保证二进制往返无损。
 */
internal object SnapshotJsonCodec {

    fun export(database: SupportSQLiteDatabase, tables: List<String>, destination: File): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        FileOutputStream(destination).bufferedWriter(Charsets.UTF_8).use { writer ->
            val json = JSONObject()
            val tableJson = JSONObject()
            tables.forEach { table ->
                val rows = JSONArray()
                database.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                    while (cursor.moveToNext()) rows.put(cursor.rowObject())
                }
                counts[table] = rows.length()
                tableJson.put(table, rows)
            }
            json.put("tables", tableJson)
            writer.write(json.toString())
        }
        return counts
    }

    fun insert(database: SupportSQLiteDatabase, tables: List<String>, source: File) {
        val tableJson = JSONObject(source.readText(Charsets.UTF_8)).getJSONObject("tables")
        tables.forEach { table ->
            val rows = tableJson.getJSONArray(table)
            if (rows.length() == 0) return@forEach
            val blobColumns = blobColumns(database, table)
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val columns = row.keys().asSequence().toList()
                val sql = "INSERT INTO `$table` (${columns.joinToString(",") { "`$it`" }}) " +
                    "VALUES (${columns.joinToString(",") { "?" }})"
                val values = columns.map { column -> row.bindValue(column, blobColumns) }.toTypedArray()
                database.execSQL(sql, values)
            }
        }
    }

    /** `PRAGMA table_info` 里声明为 BLOB 的列；判定不依赖列名，任何表都适用。 */
    private fun blobColumns(database: SupportSQLiteDatabase, table: String): Set<String> {
        val result = mutableSetOf<String>()
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            while (cursor.moveToNext()) {
                if (cursor.getString(typeIndex).equals("BLOB", ignoreCase = true)) {
                    result += cursor.getString(nameIndex)
                }
            }
        }
        return result
    }

    private fun JSONObject.bindValue(column: String, blobColumns: Set<String>): Any? {
        val value = opt(column)
        if (value == JSONObject.NULL) return null
        if (column in blobColumns && value is String) {
            return android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
        }
        return value
    }

    private fun Cursor.rowObject(): JSONObject {
        val row = JSONObject()
        for (index in 0 until columnCount) {
            val value: Any = when (getType(index)) {
                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                Cursor.FIELD_TYPE_INTEGER -> getLong(index)
                Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
                Cursor.FIELD_TYPE_BLOB -> android.util.Base64.encodeToString(getBlob(index), android.util.Base64.NO_WRAP)
                else -> getString(index)
            }
            row.put(getColumnName(index), value)
        }
        return row
    }
}
