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
 * Two v10 shapes of `e2_calibration_metadata` exist on branch-era installs: the
 * originally shipped digest-bound acceptance columns, and the v10 A2 refactor's
 * in-place rename to the staleness-record columns (repaired by MIGRATION_10_11).
 * Both must migrate: digest-bound acceptances cannot be honored under the
 * attestation model, so they fall back to AUTO; everything else is preserved.
 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

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
    fun migrate10To11_fromDigestShape_downgradesAcceptedToAutoAndKeepsTheRest() {
        migrateVersion10Database(createMetadata = { db ->
            db.execSQL(
                """
                CREATE TABLE `e2_calibration_metadata` (
                    `resultUuid` TEXT NOT NULL,
                    `disposition` TEXT NOT NULL,
                    `acceptedReviewDigestSchema` TEXT,
                    `acceptedReviewDigestAlgorithm` TEXT,
                    `acceptedReviewDigestHexLower` TEXT,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`resultUuid`),
                    FOREIGN KEY(`resultUuid`) REFERENCES `blood_test_results`(`uuid`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO e2_calibration_metadata VALUES
                    ('result-auto', 'AUTO', NULL, NULL, NULL, 100),
                    ('result-accepted', 'ACCEPTED', 'digest-schema/v1', 'SHA-256',
                     '${"a".repeat(64)}', 200),
                    ('result-excluded', 'EXCLUDED', NULL, NULL, NULL, 300)
                """.trimIndent()
            )
        }).use { migrated ->
            val db = migrated.database
            assertRecordShapeColumns(db)

            assertMetadataRow(db, "result-auto", "AUTO", recordExpected = false, updatedAt = 100)
            // The digest acceptance has no staleness record to carry: back to review.
            assertMetadataRow(
                db, "result-accepted", "AUTO", recordExpected = false, updatedAt = 200,
            )
            assertMetadataRow(
                db, "result-excluded", "EXCLUDED", recordExpected = false, updatedAt = 300,
            )
        }
    }

    @Test
    fun migrate10To11_fromRecordShape_preservesEveryRowIntact() {
        migrateVersion10Database(createMetadata = { db ->
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
                    FOREIGN KEY(`resultUuid`) REFERENCES `blood_test_results`(`uuid`)
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
        }).use { migrated ->
            val db = migrated.database
            assertRecordShapeColumns(db)

            assertMetadataRow(db, "result-auto", "AUTO", recordExpected = false, updatedAt = 100)
            assertMetadataRow(
                db, "result-accepted", "ACCEPTED", recordExpected = true, updatedAt = 200,
            )
            db.query(
                """
                SELECT acceptedModelVersion, acceptedSourceValueBits,
                       acceptedCollectedAtEpochMillis
                FROM e2_calibration_metadata WHERE resultUuid = 'result-accepted'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("route-calibration/v10", cursor.getString(0))
                assertEquals("4059000000000000", cursor.getString(1))
                assertEquals(555L, cursor.getLong(2))
            }
            assertMetadataRow(
                db, "result-excluded", "EXCLUDED", recordExpected = false, updatedAt = 300,
            )
        }
    }

    private fun assertRecordShapeColumns(db: SupportSQLiteDatabase) {
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
                "updatedAtEpochMillis",
            ),
            columns,
        )

        var foreignKeyTarget: String? = null
        db.query("PRAGMA foreign_key_list(`e2_calibration_metadata`)").use { cursor ->
            val tableIndex = cursor.getColumnIndexOrThrow("table")
            if (cursor.moveToFirst()) {
                foreignKeyTarget = cursor.getString(tableIndex)
                assertFalse(cursor.moveToNext())
            }
        }
        assertEquals("blood_test_results", foreignKeyTarget)
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
                   acceptedCollectedAtEpochMillis IS NOT NULL
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

    private fun migrateVersion10Database(
        createMetadata: (SupportSQLiteDatabase) -> Unit,
    ): MigratedDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(10) {
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
                            createMetadata(db)
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
            MIGRATION_10_11.migrate(db)
            db.version = 11
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
