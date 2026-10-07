package com.mkx.hrttracker.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration9To10Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HrtTrackerDatabase::class.java,
        emptyList(),
        cipherFactory(),
    )

    @get:Rule
    val testName: TestName = TestName()

    private val testDb get() = "migration-test-db-${testName.methodName}"

    @Before
    fun loadSqlCipher() {
        System.loadLibrary("sqlcipher")
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(testDb)
    }

    @Test
    fun migrate9To10_matchesRoomSchema_andStartsCalibrationMetadataEmpty() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        migrationHelper.createDatabase(testDb, 9).use { version9 ->
            assertFalse(tableExists(version9, "e2_calibration_metadata"))
            insertBloodTestResult(version9)
        }

        migrationHelper.runMigrationsAndValidate(
            testDb,
            10,
            true,
            MIGRATION_9_10,
        ).use { migrated ->
            assertTableExists(migrated, "e2_calibration_metadata")
            assertEquals(0L, rowCount(migrated, "e2_calibration_metadata"))
            assertBloodTestResultSurvived(migrated)
        }

        // MigrationTestHelper validates the exported JSON. Reopening the same encrypted
        // file through Room additionally exercises Room's runtime identity-hash check
        // with the SQLCipher factory used by production.
        val roomDatabase = Room.databaseBuilder(
            context,
            HrtTrackerDatabase::class.java,
            testDb,
        )
            .openHelperFactory(cipherFactory())
            .addMigrations(MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()
        migrationHelper.closeWhenFinished(roomDatabase)

        val db = roomDatabase.openHelper.writableDatabase
        db.execSQL("PRAGMA foreign_keys=ON")
        assertEquals(0L, rowCount(db, "e2_calibration_metadata"))
        assertBloodTestResultSurvived(db)

        // Review metadata is result-owned: deleting the result cascades.
        db.execSQL(
            """
            INSERT INTO e2_calibration_metadata (
                resultUuid, disposition, updatedAtEpochMillis
            ) VALUES ('result-1', 'EXCLUDED', 1000)
            """.trimIndent()
        )
        db.execSQL("DELETE FROM blood_test_results WHERE uuid = 'result-1'")
        assertEquals(0L, rowCount(db, "e2_calibration_metadata"))
    }

    private fun insertBloodTestResult(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO blood_test_panels (
                uuid, collectedAtInstantEpochMillis, collectedAtTimeZoneId, notes,
                timeSinceLastEstradiolDoseMillis, timeSinceLastTestosteroneDoseMillis,
                createdAtEpochMillis, updatedAtEpochMillis, importSourceApp, importPanelKey
            ) VALUES ('panel-1', 0, 'UTC', NULL, NULL, NULL, 0, 0, NULL, NULL)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO blood_test_results (
                uuid, panelUuid, createdAtEpochMillis, displayOrder,
                builtinAnalyteKey, customAnalyteUuid, value, unitSnapshot,
                canonicalValue, importSourceApp, importExternalId
            ) VALUES (
                'result-1', 'panel-1', 0, 0,
                'e2', NULL, 100.0, 'pg/mL', 100.0, NULL, NULL
            )
            """.trimIndent()
        )
    }

    private fun assertBloodTestResultSurvived(db: SupportSQLiteDatabase) {
        db.query(
            "SELECT panelUuid, canonicalValue FROM blood_test_results WHERE uuid = ?",
            arrayOf("result-1"),
        ).use { cursor ->
            assertTrue("Seeded v9 blood-test result was not preserved", cursor.moveToFirst())
            assertEquals("panel-1", cursor.getString(0))
            assertEquals(100.0, cursor.getDouble(1), 0.0)
        }
    }

    private fun assertTableExists(db: SupportSQLiteDatabase, table: String) {
        assertTrue("Missing table $table", tableExists(db, table))
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        return db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }
    }

    private fun rowCount(db: SupportSQLiteDatabase, table: String): Long {
        return db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
    }

    private companion object {
        fun cipherFactory(): SupportOpenHelperFactory {
            return SupportOpenHelperFactory(
                "migration-test-passphrase".toByteArray(Charsets.UTF_8)
            )
        }
    }
}
