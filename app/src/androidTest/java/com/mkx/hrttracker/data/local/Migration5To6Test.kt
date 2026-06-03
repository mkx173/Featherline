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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.Closeable

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {

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
    fun migrate5To6_addsCategoryAppliedAtIndex() {
        migrateVersion5Database { db ->
            db.execSQL(
                """
                INSERT INTO medication_log_entries (
                    uuid, category, medicineUuid, applicationType,
                    doseInstructionKind, tabletFractionNumerator,
                    tabletFractionDenominator, doseVolumeMl, doseWeightGrams,
                    equivalentE2Mg, sourceGroupUuid, scheduleTimeUuid,
                    appliedAtEpochMillis, appliedAtTimeZoneId, scheduledForIso,
                    count, gelApplicationArea, doseAmountDelta
                ) VALUES (
                    'aa-old', 'ANTIANDROGEN', 'm-aa', 'ORAL', 'TABLET_FRACTION',
                    1, 1, NULL, NULL, NULL, NULL, NULL, 1000, 'UTC',
                    NULL, 1, 'DEFAULT', NULL
                ), (
                    'aa-new', 'ANTIANDROGEN', 'm-aa', 'ORAL', 'TABLET_FRACTION',
                    1, 1, NULL, NULL, NULL, NULL, NULL, 2000, 'UTC',
                    NULL, 1, 'DEFAULT', NULL
                )
                """.trimIndent()
            )
        }.use { migrated ->
            val db = migrated.database

            assertIndex(
                db = db,
                table = "medication_log_entries",
                index = "index_medication_log_entries_category_appliedAtEpochMillis",
                columns = listOf("category", "appliedAtEpochMillis"),
            )

            db.query(
                """
                SELECT uuid
                FROM medication_log_entries
                WHERE category = 'ANTIANDROGEN'
                ORDER BY appliedAtEpochMillis ASC
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("aa-old", cursor.getString(0))
                assertTrue(cursor.moveToNext())
                assertEquals("aa-new", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private fun migrateVersion5Database(
        seed: (SupportSQLiteDatabase) -> Unit,
    ): MigratedDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV5Schema(db)
                            seed(db)
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
            MIGRATION_5_6.migrate(db)
            db.version = 6
            return MigratedDatabase(helper, db)
        } catch (throwable: Throwable) {
            db.close()
            helper.close()
            throw throwable
        }
    }

    private fun assertIndex(
        db: SupportSQLiteDatabase,
        table: String,
        index: String,
        columns: List<String>,
    ) {
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == index) {
                    found = true
                    break
                }
            }
            assertTrue("Missing index $index", found)
        }

        db.query("PRAGMA index_info($index)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val actualColumns = buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
            assertEquals(columns, actualColumns)
        }
    }

    private fun createV5Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE medication_log_entries (
                uuid TEXT NOT NULL PRIMARY KEY,
                category TEXT NOT NULL,
                medicineUuid TEXT,
                applicationType TEXT NOT NULL,
                doseInstructionKind TEXT NOT NULL,
                tabletFractionNumerator INTEGER,
                tabletFractionDenominator INTEGER,
                doseVolumeMl REAL,
                doseWeightGrams REAL,
                equivalentE2Mg REAL,
                sourceGroupUuid TEXT,
                scheduleTimeUuid TEXT,
                appliedAtEpochMillis INTEGER NOT NULL,
                appliedAtTimeZoneId TEXT NOT NULL,
                scheduledForIso TEXT,
                count INTEGER NOT NULL DEFAULT 1,
                gelApplicationArea TEXT NOT NULL DEFAULT 'DEFAULT',
                doseAmountDelta REAL
            )
            """.trimIndent()
        )
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
