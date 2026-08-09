package com.mkx.hrttracker.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.Closeable

/**
 * v11 → v12 adds the acceptedUnitId unit binding to the acceptance record
 * (Phase-3 #8). Pre-v12 acceptances carry no unit binding and cannot honor it:
 * ACCEPTED rows fall back to AUTO with the record cleared, mirroring
 * MIGRATION_10_11's digest-row handling; AUTO and EXCLUDED rows are preserved.
 */
@RunWith(AndroidJUnit4::class)
class Migration11To12Test {

    @get:Rule
    val testName: TestName = TestName()

    private val testDb: String
        get() = "migration-test-db-${testName.methodName}"

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .deleteDatabase(testDb)
    }

    @Test
    fun migrate11To12_downgradesAcceptedToAutoAndKeepsTheRest() {
        migrateVersion11Database().use { migrated ->
            val db = migrated.database

            val columns = mutableListOf<String>()
            db.query("PRAGMA table_info(`e2_calibration_metadata`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }
            assertEquals(
                listOf(
                    "resultUuid", "disposition", "acceptedModelVersion",
                    "acceptedSourceValueBits", "acceptedCollectedAtEpochMillis",
                    "updatedAtEpochMillis", "acceptedUnitId",
                ),
                columns,
            )

            assertMetadataRow(db, "result-auto", "AUTO", recordExpected = false, updatedAt = 100)
            // The pre-unit acceptance cannot honor the unit binding: back to review.
            assertMetadataRow(
                db, "result-accepted", "AUTO", recordExpected = false, updatedAt = 200,
            )
            assertMetadataRow(
                db, "result-excluded", "EXCLUDED", recordExpected = false, updatedAt = 300,
            )
        }
    }

    private fun assertMetadataRow(
        db: SupportSQLiteDatabase,
        resultUuid: String,
        disposition: String,
        recordExpected: Boolean,
        updatedAt: Long,
    ) {
        db.query(
            """
            SELECT disposition, updatedAtEpochMillis,
                   acceptedModelVersion IS NOT NULL OR
                   acceptedSourceValueBits IS NOT NULL OR
                   acceptedCollectedAtEpochMillis IS NOT NULL OR
                   acceptedUnitId IS NOT NULL
            FROM e2_calibration_metadata WHERE resultUuid = '$resultUuid'
            """.trimIndent()
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                fail("Missing row $resultUuid")
            }
            assertEquals(disposition, cursor.getString(0))
            assertEquals(updatedAt, cursor.getLong(1))
            assertEquals(if (recordExpected) 1 else 0, cursor.getInt(2))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun migrateVersion11Database(): MigratedDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(11) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // Minimal parent table: only what the FK references.
                            db.execSQL(
                                """
                                CREATE TABLE `blood_test_results` (
                                    `uuid` TEXT NOT NULL PRIMARY KEY
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO blood_test_results VALUES
                                    ('result-auto'), ('result-accepted'), ('result-excluded')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE `e2_calibration_metadata` (
                                    `resultUuid` TEXT NOT NULL,
                                    `disposition` TEXT NOT NULL,
                                    `acceptedModelVersion` TEXT,
                                    `acceptedSourceValueBits` TEXT,
                                    `acceptedCollectedAtEpochMillis` INTEGER,
                                    `updatedAtEpochMillis` INTEGER NOT NULL,
                                    PRIMARY KEY(`resultUuid`),
                                    FOREIGN KEY(`resultUuid`)
                                        REFERENCES `blood_test_results`(`uuid`)
                                        ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO e2_calibration_metadata VALUES
                                    ('result-auto', 'AUTO', NULL, NULL, NULL, 100),
                                    ('result-accepted', 'ACCEPTED', 'route-calibration/v10',
                                     '4059000000000000', 555, 200),
                                    ('result-excluded', 'EXCLUDED', NULL, NULL, NULL, 300)
                                """.trimIndent()
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    }
                )
                .build()
        )

        val db = try {
            helper.writableDatabase
        } catch (throwable: Throwable) {
            helper.close()
            throw throwable
        }

        try {
            MIGRATION_11_12.migrate(db)
            db.version = 12
            return MigratedDatabase(helper, db)
        } catch (throwable: Throwable) {
            db.close()
            helper.close()
            throw throwable
        }
    }

    private class MigratedDatabase(
        val helper: SupportSQLiteOpenHelper,
        val database: SupportSQLiteDatabase,
    ) : Closeable {
        override fun close() {
            database.close()
            helper.close()
        }
    }
}
