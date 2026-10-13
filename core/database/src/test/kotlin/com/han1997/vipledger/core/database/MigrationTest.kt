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
import kotlin.test.assertFalse
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
                "auto_config", "auto_binding", "auto_candidate", "auto_evidence",
                "budget_limit", "budget_alert",
                "savings_goal", "savings_link", "home_card", "asset_baseline",
                "reimbursement", "reimbursement_item", "reimbursement_receipt", "lending", "lending_link",
                "installment_link", "investment", "investment_link",
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

    @Test
    fun versionOneUpgradesWithoutLosingExistingRows() = upgradeFrom(1)

    @Test
    fun versionTwoUpgradesWithoutLosingExistingRows() = upgradeFrom(2)

    @Test
    fun versionThreeUpgradesWithoutLosingExistingRows() = upgradeFrom(3)

    @Test
    fun versionFourUpgradesWithoutLosingExistingRows() = upgradeFrom(4)

    @Test
    fun versionFiveUpgradesWithoutLosingExistingRows() = upgradeFrom(5)

    @Test
    fun versionSixUpgradesAndAddsCardNoColumn() = upgradeFrom(
        version = 6,
        extraAssert = { sql ->
            sql.query("SELECT card_no FROM account WHERE id='cash'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
            }
        },
    )

    @Test
    fun migrationFiveToSixBackfillsLegacyPaidPeriods() = upgradeFrom(
        version = 5,
        extraSetup = { sql ->
            sql.execSQL(
                "INSERT INTO installment_plan(id,ledger_id,account_id,name,method,principal_cents,periods," +
                    "annual_rate_bp,upfront_fee_cents,per_period_fee_cents,first_due_date,due_day,status," +
                    "remark,created_at,updated_at,deleted_at) VALUES('plan','old','cash','包年','EQUAL_PRINCIPAL'," +
                    "120000,2,1200,0,0,1,NULL,'ACTIVE',NULL,1,1,NULL)",
            )
            sql.execSQL(
                "INSERT INTO installment_period(id,plan_id,period_no,due_date,principal_cents,interest_cents," +
                    "fee_cents,total_cents,status,paid_txn_id,paid_at) VALUES('sp1','plan',1,1,60000,1000,500," +
                    "61500,'PAID','purchase',1)",
            )
            sql.execSQL(
                "INSERT INTO installment_period(id,plan_id,period_no,due_date,principal_cents,interest_cents," +
                    "fee_cents,total_cents,status,paid_txn_id,paid_at) VALUES('sp2','plan',2,1,60000,1000,500," +
                    "61500,'PENDING',NULL,NULL)",
            )
        },
        extraAssert = { sql ->
            sql.query(
                "SELECT paid_principal_cents,paid_interest_cents,paid_fee_cents FROM installment_period WHERE id='sp1'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(60000L, it.getLong(0))
                assertEquals(1000L, it.getLong(1))
                assertEquals(500L, it.getLong(2))
            }
            sql.query("SELECT paid_principal_cents FROM installment_period WHERE id='sp2'").use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            sql.query("SELECT origin,disbursement_account_id,disbursed_at FROM installment_plan WHERE id='plan'").use {
                assertTrue(it.moveToFirst())
                assertEquals("EXISTING", it.getString(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
        },
    )

    private fun upgradeFrom(
        version: Int,
        extraSetup: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit = {},
        extraAssert: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit = {},
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-${System.nanoTime()}.db"
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val old = Json.parseToJsonElement(assets.open("${LedgerDatabase::class.java.name}/$version.json")
            .bufferedReader().use { it.readText() }).jsonObject.getValue("database").jsonObject
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(sql: androidx.sqlite.db.SupportSQLiteDatabase) {
                        old.getValue("entities").jsonArray.forEach { item ->
                            val entity = item.jsonObject
                            val table = entity.getValue("tableName").jsonPrimitive.content
                            sql.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                            entity["indices"]?.jsonArray.orEmpty().forEach {
                                sql.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                            }
                        }
                        old.getValue("setupQueries").jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
                    }
                    override fun onUpgrade(sql: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        helper.writableDatabase.execSQL("INSERT INTO ledger(id,name,currency_code,sort_order,is_default,created_at,updated_at) VALUES('old','Old ledger','CNY',0,1,1,1)")
        helper.writableDatabase.execSQL("INSERT INTO account(id,ledger_id,name,type,initial_balance_cents,current_balance_cents,include_in_net_worth,is_archived,sort_order,created_at,updated_at) VALUES('cash','old','Cash','CASH',10000,8766,1,0,0,1,1)")
        helper.writableDatabase.execSQL("INSERT INTO txn(id,ledger_id,type,amount_cents,account_id,occurred_at,title,remark,source,draft_status,exclude_from_stats,created_at,updated_at) VALUES('purchase','old','EXPENSE',1234,'cash',1,'Lunch','','MANUAL','CONFIRMED',0,1,1)")
        extraSetup(helper.writableDatabase)
        helper.close()
        val upgraded = Room.databaseBuilder(context, LedgerDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            upgraded.openHelper.writableDatabase.query("SELECT balance_cents,included,captured_at FROM asset_baseline WHERE account_id='cash'").use {
                if (version < 4) {
                    assertTrue(it.moveToFirst()); assertEquals(8766L, it.getLong(0)); assertEquals(1, it.getInt(1)); assertTrue(it.getLong(2) > 0)
                } else {
                    assertFalse(it.moveToFirst(), "从 schema $version 升级不会补写基线，基线应在首次资产观察时生成")
                }
            }
            upgraded.openHelper.writableDatabase.query("SELECT COUNT(*) FROM budget_limit").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            upgraded.openHelper.writableDatabase.query("SELECT name FROM ledger WHERE id='old'").use {
                assertTrue(it.moveToFirst()); assertEquals("Old ledger", it.getString(0))
            }
            upgraded.openHelper.writableDatabase.query("SELECT COUNT(*) FROM auto_candidate").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            upgraded.openHelper.writableDatabase.query("SELECT amount_cents, refund_of_txn_id FROM txn WHERE id='purchase'").use {
                assertTrue(it.moveToFirst()); assertEquals(1234L, it.getLong(0)); assertTrue(it.isNull(1))
            }
            upgraded.openHelper.writableDatabase.query("SELECT current_balance_cents FROM account WHERE id='cash'").use {
                assertTrue(it.moveToFirst()); assertEquals(8766L, it.getLong(0))
            }
            listOf(
                "reimbursement", "reimbursement_item", "reimbursement_receipt", "lending", "lending_link",
                "installment_link", "investment", "investment_link",
            ).forEach { table ->
                upgraded.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use {
                    assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0), "迁移后 $table 应为空")
                }
            }
            extraAssert(upgraded.openHelper.writableDatabase)
        } finally { upgraded.close(); context.deleteDatabase(name) }
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
