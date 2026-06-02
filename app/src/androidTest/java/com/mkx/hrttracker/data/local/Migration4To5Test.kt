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
class Migration4To5Test {

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
    fun migrate4To5_addsNullableDoseAmountDelta() {
        migrateVersion4Database { db ->
            db.execSQL(
                """
                INSERT INTO medication_log_entries (
                    uuid, category, medicineUuid, applicationType,
                    doseInstructionKind, tabletFractionNumerator,
                    tabletFractionDenominator, doseVolumeMl, doseWeightGrams,
                    equivalentE2Mg, sourceGroupUuid, scheduleTimeUuid,
                    appliedAtEpochMillis, appliedAtTimeZoneId, scheduledForIso,
                    count, gelApplicationArea
                ) VALUES (
                    'u1', 'ESTRADIOL', NULL, 'INJECTION', 'VOLUME_ML',
                    NULL, NULL, 0.5, NULL, 1.0, NULL, NULL, 0, 'UTC',
                    NULL, 1, 'DEFAULT'
                )
                """.trimIndent()
            )
        }.use { migrated ->
            val db = migrated.database
            db.query(
                """
                SELECT uuid, doseAmountDelta
                FROM medication_log_entries
                WHERE uuid = 'u1'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("u1", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertFalse(cursor.moveToNext())
            }
            assertColumn(
                db,
                "medication_log_entries",
                "doseAmountDelta",
                "REAL",
                notNull = false,
                defaultValue = null,
            )
        }
    }

    private fun migrateVersion4Database(
        seed: (SupportSQLiteDatabase) -> Unit,
    ): MigratedDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(4) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV4Schema(db)
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
            MIGRATION_4_5.migrate(db)
            db.version = 5
            return MigratedDatabase(helper, db)
        } catch (throwable: Throwable) {
            db.close()
            helper.close()
            throw throwable
        }
    }

    private fun assertColumn(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
        type: String,
        notNull: Boolean,
        defaultValue: String?,
    ) {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")

            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    assertEquals(type, cursor.getString(typeIndex))
                    assertEquals(notNull, cursor.getInt(notNullIndex) == 1)
                    if (defaultValue == null) {
                        assertTrue(cursor.isNull(defaultIndex))
                    } else {
                        assertEquals(defaultValue, cursor.getString(defaultIndex))
                    }
                    return
                }
            }
        }
        throw AssertionError("Missing column $table.$column")
    }

    private fun createV4Schema(db: SupportSQLiteDatabase) {
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
                gelApplicationArea TEXT NOT NULL DEFAULT 'DEFAULT'
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
