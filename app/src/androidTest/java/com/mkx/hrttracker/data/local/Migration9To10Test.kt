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
class Migration9To10Test {
    @get:Rule val testName: TestName = TestName()
    private val testDb get() = "migration-test-db-${testName.methodName}"

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(testDb)
    }

    @Test
    fun migrate9To10_createsEmptyPopulationSafeTables_andMetadataCascadesWithResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE blood_test_results " +
                                "(uuid TEXT NOT NULL PRIMARY KEY)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        try {
            db.execSQL("PRAGMA foreign_keys=ON")
            MIGRATION_9_10.migrate(db)
            db.version = 10

            assertTableExists(db, "e2_calibration_metadata")
            assertTableExists(db, "pk_calibration_display_artifact")
            assertEquals(0L, rowCount(db, "e2_calibration_metadata"))
            assertEquals(0L, rowCount(db, "pk_calibration_display_artifact"))

            db.execSQL("INSERT INTO blood_test_results (uuid) VALUES ('result-1')")
            db.execSQL(
                """
                INSERT INTO e2_calibration_metadata (
                    resultUuid, disposition, acceptedReviewDigestSchema,
                    acceptedReviewDigestAlgorithm, acceptedReviewDigestHexLower,
                    updatedAtEpochMillis
                ) VALUES ('result-1', 'EXCLUDED', NULL, NULL, NULL, 1000)
                """.trimIndent()
            )
            db.execSQL("DELETE FROM blood_test_results WHERE uuid = 'result-1'")
            assertEquals(0L, rowCount(db, "e2_calibration_metadata"))

            insertDisplayArtifact(db)
            try {
                insertDisplayArtifact(db)
                fail("Expected a duplicate artifact primary key to be rejected.")
            } catch (_: SQLiteConstraintException) {
                // The schema rejects duplicate ID=1 rows. The repository, not a
                // schema CHECK constraint, enforces that ID=1 is the sole logical
                // singleton identifier used for the atomic list/map artifact.
            }
        } finally {
            db.close()
            helper.close()
        }
    }

    private fun insertDisplayArtifact(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO pk_calibration_display_artifact (
                singletonId, homeSnapshotGeneration, schema, calibrationModelVersion,
                resultInputDigestSchema, resultInputDigestAlgorithm,
                resultInputDigestHexLower, promotedRouteStableIds,
                injectionLogScale, patchLogScale, gelLogScale,
                oralLogScale, sublingualLogScale
            ) VALUES (
                1, 7, 'hrttracker.calibration-display/v9', 'route-v9-final',
                'hrttracker.fit-input/test', 'SHA-256',
                'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                'patch', NULL, NULL, NULL, NULL, NULL
            )
            """.trimIndent()
        )
    }

    private fun assertTableExists(db: SupportSQLiteDatabase, table: String) {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { cursor -> assertTrue("Missing table $table", cursor.moveToFirst()) }
    }

    private fun rowCount(db: SupportSQLiteDatabase, table: String): Long {
        return db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
    }
}
