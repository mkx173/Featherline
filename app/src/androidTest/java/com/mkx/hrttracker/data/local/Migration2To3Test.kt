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
class Migration2To3Test {

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
    fun migrate2To3_existingMedicineRow_comesUpUntracked() {
        migrateVersion2Database { db ->
            db.execSQL(
                """
                INSERT INTO medicines (
                    uuid, selectionKind, medicationKey, customMedicationName,
                    customMedicationNameNormalized, category, preparationType,
                    strengthMgPerTablet, strengthMgPerVial, concentrationMgPerMl,
                    vialVolumeMl, concentrationPercent, sachetWeightGrams,
                    containerWeightGrams, patchTotalMg, patchReleaseRateMcgPerDay,
                    displayName, identityKey, createdAtEpochMillis,
                    updatedAtEpochMillis, archivedAtEpochMillis, displayDoseUnit
                ) VALUES (
                    'm1', 'CATALOG', 'EV_2MG', NULL, NULL,
                    'ESTRADIOL', 'PILL', 2.0, NULL, NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL, NULL, 'C|EV_2MG|PILL|s=2',
                    1000, 1000, NULL, 'MG'
                )
                """.trimIndent()
            )
        }.use { migrated ->
            val db = migrated.database
            db.query(
                "SELECT trackingEnabled, stockUnitsRemaining, " +
                    "stockUnitsLastTotal, openContainerAmount, " +
                    "warnAtDaysRemaining, stockGeneration FROM medicines WHERE uuid = 'm1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertEquals(14, cursor.getInt(4))
                assertEquals(0L, cursor.getLong(5))
                assertFalse(cursor.moveToNext())
            }
            assertColumn(db, "medicines", "trackingEnabled", "INTEGER", notNull = true, defaultValue = "0")
            assertColumn(db, "medicines", "stockUnitsRemaining", "REAL", notNull = false, defaultValue = null)
            assertColumn(db, "medicines", "stockUnitsLastTotal", "REAL", notNull = false, defaultValue = null)
            assertColumn(db, "medicines", "openContainerAmount", "REAL", notNull = false, defaultValue = null)
            assertColumn(db, "medicines", "warnAtDaysRemaining", "INTEGER", notNull = true, defaultValue = "14")
            assertColumn(db, "medicines", "stockGeneration", "INTEGER", notNull = true, defaultValue = "0")
        }
    }

    @Test
    fun migrate2To3_existingLogRow_hasNullStockColumns() {
        migrateVersion2Database { db ->
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
                    'l1', 'ESTRADIOL', 'm1', 'ORAL', 'WHOLE_UNIT', NULL, NULL,
                    NULL, NULL, NULL, NULL, NULL, 1000, 'UTC', NULL, 1, 'DEFAULT'
                )
                """.trimIndent()
            )
        }.use { migrated ->
            val db = migrated.database
            db.query(
                "SELECT stockDeductionUnits, stockGeneration FROM medication_log_entries WHERE uuid = 'l1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
                assertFalse(cursor.moveToNext())
            }
            assertColumn(
                db,
                "medication_log_entries",
                "stockDeductionUnits",
                "REAL",
                notNull = false,
                defaultValue = null,
            )
            assertColumn(
                db,
                "medication_log_entries",
                "stockGeneration",
                "INTEGER",
                notNull = false,
                defaultValue = null,
            )
        }
    }

    private fun migrateVersion2Database(
        seed: (SupportSQLiteDatabase) -> Unit,
    ): MigratedDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV2Schema(db)
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
            MIGRATION_2_3.migrate(db)
            db.version = 3
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

    private fun createV2Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE medicines (
                uuid TEXT NOT NULL PRIMARY KEY,
                selectionKind TEXT NOT NULL,
                medicationKey TEXT,
                customMedicationName TEXT,
                customMedicationNameNormalized TEXT,
                category TEXT NOT NULL,
                preparationType TEXT NOT NULL,
                strengthMgPerTablet REAL,
                strengthMgPerVial REAL,
                concentrationMgPerMl REAL,
                vialVolumeMl REAL,
                concentrationPercent REAL,
                sachetWeightGrams REAL,
                containerWeightGrams REAL,
                patchTotalMg REAL,
                patchReleaseRateMcgPerDay REAL,
                displayName TEXT,
                identityKey TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                archivedAtEpochMillis INTEGER,
                displayDoseUnit TEXT NOT NULL DEFAULT 'MG'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX index_medicines_identityKey ON medicines(identityKey)")
        db.execSQL("CREATE INDEX index_medicines_archivedAtEpochMillis ON medicines(archivedAtEpochMillis)")
        db.execSQL("CREATE INDEX index_medicines_category ON medicines(category)")

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
