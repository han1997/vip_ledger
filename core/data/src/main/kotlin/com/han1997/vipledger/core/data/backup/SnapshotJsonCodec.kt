package com.han1997.vipledger.core.data.backup

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.Writer

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
 * **导出是流式的**：按 `tables` 顺序逐表、逐行把 JSON 直接写进目标文件，
 * 内存里同时只驻留一行（一个 [JSONObject]）加写入缓冲；不会先构造整库的
 * 内存对象、也不会先生成整份 JSON 字符串——大库备份的峰值内存因此受一行
 * 大小约束，而不是 db 大小（PRD：不把整份备份额外复制到内存）。输出的字节
 * 与早期「整体构建再 `toString()`」方案完全一致，`manifest.sha256OfDb`
 * 校验和不受影响。
 *
 * BLOB 列在 JSON 里用无换行 Base64 文本承载（JSON 没有二进制），写回时按
 * `PRAGMA table_info` 判定列类型再解码，保证二进制往返无损。
 */
internal object SnapshotJsonCodec {

    /**
     * 流式导出到 [destination]，返回「表名 → 行数」计数（供快照 manifest 使用）。
     * 逐行写出见 [export] 的 [Writer] 重载。
     */
    fun export(database: SupportSQLiteDatabase, tables: List<String>, destination: File): Map<String, Int> =
        FileOutputStream(destination).bufferedWriter(Charsets.UTF_8).use { writer ->
            export(database, tables, writer)
        }

    /**
     * 流式导出的核心：增量把 JSON 写入 [writer]。
     *
     * 单独抽出 [Writer] 重载，是为了让「逐行写出、不整库驻留内存」这一性质
     * 能被单元测试直接观测（整库构建方案只会产生一次超大 `write`）。
     * 行内序列化仍交给 [JSONObject]（每行一个），从而原样复用 `org.json` 的
     * 字符串转义与数字格式，保证与旧实现字节级一致。
     */
    internal fun export(database: SupportSQLiteDatabase, tables: List<String>, writer: Writer): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        writer.write("{\"schemaVersion\":${com.han1997.vipledger.core.database.LedgerDatabase.SCHEMA_VERSION},\"tables\":{")
        tables.forEachIndexed { tableIndex, table ->
            if (tableIndex > 0) writer.write(",")
            writer.write(JSONObject.quote(table))
            writer.write(":[")
            var count = 0
            database.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                while (cursor.moveToNext()) {
                    if (count > 0) writer.write(",")
                    writer.write(cursor.rowObject().toString())
                    count++
                }
            }
            counts[table] = count
            writer.write("]")
        }
        writer.write("}}")
        return counts
    }

    fun insert(database: SupportSQLiteDatabase, tables: List<String>, source: File) {
        val document = JSONObject(source.readText(Charsets.UTF_8))
        val version = document.optInt("schemaVersion", 1)
        require(version in 1..com.han1997.vipledger.core.database.LedgerDatabase.SCHEMA_VERSION) { "Unsupported database JSON version" }
        val tableJson = document.getJSONObject("tables")
        tables.forEach { table ->
            if (version == 1 && table.startsWith("auto_") && !tableJson.has(table)) return@forEach
            if (version < 3 && table.startsWith("budget_") && !tableJson.has(table)) return@forEach
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
