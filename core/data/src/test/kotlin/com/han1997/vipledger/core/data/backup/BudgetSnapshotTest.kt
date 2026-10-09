package com.han1997.vipledger.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.database.LedgerDatabase
import com.han1997.vipledger.core.database.entity.*
import com.han1997.vipledger.core.data.repository.BudgetRepositoryImpl
import com.han1997.vipledger.core.common.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class BudgetSnapshotTest {
    private lateinit var context: Context
    private lateinit var db: LedgerDatabase
    @BeforeTest fun setup() { context = ApplicationProvider.getApplicationContext(); db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).allowMainThreadQueries().build() }
    @AfterTest fun close() { db.close() }
    private suspend fun seed() {
        db.ledgerDao().upsert(LedgerEntity("l1", "One", createdAt = 1, updatedAt = 1))
        db.ledgerDao().upsert(LedgerEntity("l2", "Two", createdAt = 1, updatedAt = 1))
        db.budgetDao().save(listOf(BudgetLimitEntity("b1", "l1", "2026-10-01", "", 100000, 1), BudgetLimitEntity("b2", "l2", "2026-10-01", "", null, 1)))
    }
    @Test fun budgetAndClaimsRoundTripAndStayLedgerScoped() = runTest {
        seed()
        val repository = BudgetRepositoryImpl(db.budgetDao())
        assertEquals(1, repository.list("l1").size)
        assertNull(repository.list("l2").single().amount)
        val month = LocalDate(2026, 10, 1)
        assertEquals(true, (repository.claimAlert("l1", month, "", 80, 1) as AppResult.Success).value)
        assertEquals(false, (repository.claimAlert("l1", month, "", 80, 2) as AppResult.Success).value)
        assertEquals(true, (repository.claimAlert("l1", month, "", 100, 2) as AppResult.Success).value)
        val file = File(context.cacheDir, "budget-${System.nanoTime()}.zip")
        try {
            val archive = SnapshotArchive(context, db)
            val manifest = archive.create(file)
            assertEquals(2, manifest.counts["budget_limit"]); assertEquals(2, manifest.counts["budget_alert"])
            db.openHelper.writableDatabase.execSQL("DELETE FROM budget_limit")
            db.openHelper.writableDatabase.execSQL("DELETE FROM budget_alert")
            archive.restore(file)
            assertEquals(100000L, repository.list("l1").single().amount!!.cents)
            assertNull(repository.list("l2").single().amount)
            assertEquals(false, (repository.claimAlert("l1", month, "", 80, 3) as AppResult.Success).value)
            assertEquals(true, (repository.claimAlert("l1", LocalDate(2026, 11, 1), "", 80, 3) as AppResult.Success).value)
            assertEquals(true, (repository.claimAlert("l2", month, "", 80, 3) as AppResult.Success).value)
        } finally { file.delete() }
    }
    @Test fun formatTwoWithoutBudgetTablesRestoresAndReplacesExistingBudgets() = runTest {
        seed()
        val json = File(context.cacheDir, "budget-legacy-${System.nanoTime()}.json")
        val zip = File(context.cacheDir, "budget-legacy-${System.nanoTime()}.zip")
        try {
            val archive = SnapshotArchive(context, db)
            archive.exportDatabaseJson(json)
            val document = JSONObject(json.readText()).put("schemaVersion", 2)
            val tables = document.getJSONObject("tables")
            tables.remove("budget_limit"); tables.remove("budget_alert")
            val bytes = document.toString().toByteArray()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val counts = JSONObject()
            tables.keys().forEach { counts.put(it, tables.getJSONArray(it).length()) }
            val manifest = JSONObject().put("formatVersion", 2).put("createdAt", 1).put("counts", counts).put("sha256OfDb", hash)
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.json")); out.write(manifest.toString().toByteArray()); out.closeEntry()
                out.putNextEntry(ZipEntry("db.json")); out.write(bytes); out.closeEntry()
            }
            archive.restore(zip)
            assertEquals(2, db.ledgerDao().count())
            assertTrue(db.budgetDao().list("l1").isEmpty())
        } finally { json.delete(); zip.delete() }
    }
}
