package com.han1997.vipledger.core.database

import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith

/**
 * 迁移测试骨架（docs/DATA-MODEL.md 第 4 节）。
 *
 * 当前 schemaVersion = 1，没有任何迁移，所以这里守住两条不变式：
 * 1. 代码里的表结构与提交到 `core/database/schemas/` 的导出 schema 完全一致
 *    （身份哈希 = Room 对整份 schema 的指纹，改表不升版本会立刻红）；
 * 2. 版本号每加一，`ALL_MIGRATIONS` 里必须有一条对应的 `Migration(n, n+1)`。
 *
 * 说明：Room 2.8 的 `MigrationTestHelper` 在 Robolectric 下用
 * SupportSQLite 驱动会报 driver/db-name 不匹配，等真的有迁移可测时再换成
 * 基于 `SQLiteConnection` 的 API（见 docs/DEV-NOTES.md 第 5 节）。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private lateinit var db: LedgerDatabase

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun exportedSchemaMatchesTheRuntimeSchema() {
        val schema = exportedSchema()
        assertEquals(LedgerDatabase.SCHEMA_VERSION, schema["version"]!!.jsonPrimitive.int)
        assertEquals(runtimeIdentityHash(), schema["identityHash"]!!.jsonPrimitive.content)
    }

    @Test
    fun exportedSchemaListsEveryEntity() {
        val tables = exportedSchema()["entities"]!!.jsonArray
            .map { it.jsonObject["tableName"]!!.jsonPrimitive.content }
            .toSet()
        assertEquals(
            setOf(
                "ledger",
                "account",
                "category",
                "tag",
                "txn_tag",
                "txn",
                "recurring_rule",
                "installment_plan",
                "installment_period",
                "merchant_rule",
                "attachment",
                "ml_training_sample",
            ),
            tables,
        )
    }

    @Test
    fun everyVersionGapHasAMigration() {
        val sorted = ALL_MIGRATIONS.sortedBy { it.startVersion }
        var version = 1
        sorted.forEach { migration ->
            assertTrue(
                migration.startVersion == version && migration.endVersion == version + 1,
                "迁移链断裂：期望 $version -> ${version + 1}，实际 ${migration.startVersion} -> ${migration.endVersion}",
            )
            version = migration.endVersion
        }
        assertEquals(
            LedgerDatabase.SCHEMA_VERSION,
            version,
            "schemaVersion 是 ${LedgerDatabase.SCHEMA_VERSION}，但迁移只覆盖到 $version",
        )
    }

    private fun exportedSchema(): Map<String, kotlinx.serialization.json.JsonElement> {
        val path = "${LedgerDatabase::class.java.name}/${LedgerDatabase.SCHEMA_VERSION}.json"
        val text = InstrumentationRegistry.getInstrumentation().context.assets.open(path)
            .bufferedReader()
            .use { it.readText() }
        return Json.parseToJsonElement(text).jsonObject.getValue("database").jsonObject
    }

    private fun runtimeIdentityHash(): String {
        val cursor: Cursor = db.openHelper.writableDatabase.query("SELECT identity_hash FROM room_master_table")
        return cursor.use {
            it.moveToFirst()
            it.getString(0)
        }
    }
}