package com.mkx.hrttracker.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {
    @get:Rule val testName: TestName = TestName()
    private val testDb get() = "migration-test-db-${testName.methodName}"

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(testDb)
    }

    @Test
    fun migrate7To8_createsJournalTablesWithOneNotePerDayInvariant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        try {
            MIGRATION_7_8.migrate(db)
            db.version = 8

            assertTableExists(db, "tracked_dates")
            assertTableExists(db, "notes")
            assertUniqueIndex(db, "notes", "index_notes_dateIso")

            db.execSQL(
                "INSERT INTO notes (uuid, dateIso, text, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES ('n1', '2026-06-16', 'first', 1000, 1000)"
            )
            try {
                db.execSQL(
                    "INSERT INTO notes (uuid, dateIso, text, createdAtEpochMillis, updatedAtEpochMillis) " +
                        "VALUES ('n2', '2026-06-16', 'second', 1000, 1000)"
                )
                fail("Expected SQLiteConstraintException for duplicate note date")
            } catch (_: SQLiteConstraintException) {
                // Expected: one note per day.
            }
        } finally {
            db.close()
            helper.close()
        }
    }

    private fun assertTableExists(db: SupportSQLiteDatabase, table: String) {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { cursor -> assertTrue("Missing table $table", cursor.moveToFirst()) }
    }

    private fun assertUniqueIndex(db: SupportSQLiteDatabase, table: String, index: String) {
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == index) {
                    assertEquals(1, cursor.getInt(uniqueIndex)); return
                }
            }
        }
        fail("Missing index $index")
    }
}
