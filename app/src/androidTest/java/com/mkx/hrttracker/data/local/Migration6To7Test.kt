package com.mkx.hrttracker.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.Closeable

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {

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
    fun migrate6To7_addsImportProvenanceColumnsAndIndexes() {
        migrateVersion6Database { db ->
            seedVersion6Rows(db)
        }.use { migrated ->
            val db = migrated.database

            assertColumn(
                db,
                "medicines",
                "importedFromExternalTracker",
                "INTEGER",
                notNull = true,
                defaultValue = "0",
            )
            assertColumn(
                db,
                "medication_log_entries",
                "importSourceApp",
                "TEXT",
                notNull = false,
                defaultValue = null,
            )
            assertColumn(
                db,
                "medication_log_entries",
                "importExternalId",
                "TEXT",
                notNull = false,
                defaultValue = null,
            )
            assertColumn(
                db,
                "blood_test_panels",
                "importSourceApp",
                "TEXT",
                notNull = false,
                defaultValue = null,
            )
            assertColumn(
                db,
                "blood_test_panels",
                "importPanelKey",
                "INTEGER",
                notNull = false,
                defaultValue = null,
            )
            assertColumn(
                db,
                "blood_test_results",
                "importSourceApp",
                "TEXT",
                notNull = false,
                defaultValue = null,
            )
            assertColumn(
                db,
                "blood_test_results",
                "importExternalId",
                "TEXT",
                notNull = false,
                defaultValue = null,
            )

            assertUniqueIndex(db, "medicines", "index_medicines_identityKey")
            assertUniqueIndex(
                db,
                "medication_log_entries",
                "index_medication_log_entries_importSourceApp_importExternalId",
            )
            assertUniqueIndex(
                db,
                "blood_test_panels",
                "index_blood_test_panels_importSourceApp_importPanelKey",
            )
            assertUniqueIndex(
                db,
                "blood_test_results",
                "index_blood_test_results_importSourceApp_importExternalId",
            )

            assertExistingRowsUseDefaults(db)
            assertNullProvenanceRowsAreAllowed(db)
            assertDuplicateNonNullProvenanceIsRejected(db)
        }
    }

    private fun migrateVersion6Database(
        seed: (SupportSQLiteDatabase) -> Unit,
    ): MigratedDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV6Schema(db)
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
            MIGRATION_6_7.migrate(db)
            db.version = 7
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
            val defaultValueIndex = cursor.getColumnIndexOrThrow("dflt_value")

            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    assertEquals(type, cursor.getString(typeIndex))
                    assertEquals(if (notNull) 1 else 0, cursor.getInt(notNullIndex))
                    if (defaultValue == null) {
                        assertNull(cursor.getString(defaultValueIndex))
                    } else {
                        assertEquals(defaultValue, cursor.getString(defaultValueIndex))
                    }
                    return
                }
            }
        }

        fail("Missing column $table.$column")
    }

    private fun assertUniqueIndex(
        db: SupportSQLiteDatabase,
        table: String,
        index: String,
    ) {
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == index) {
                    assertEquals("Index $index should be unique", 1, cursor.getInt(uniqueIndex))
                    return
                }
            }
        }

        fail("Missing index $index")
    }

    private fun assertExistingRowsUseDefaults(db: SupportSQLiteDatabase) {
        db.query(
            """
            SELECT importedFromExternalTracker
            FROM medicines
            WHERE uuid = 'medicine-seed'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
        }

        db.query(
            """
            SELECT importSourceApp, importExternalId
            FROM medication_log_entries
            WHERE uuid = 'log-seed'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertFalse(cursor.moveToNext())
        }

        db.query(
            """
            SELECT importSourceApp, importPanelKey
            FROM blood_test_panels
            WHERE uuid = 'panel-seed'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertFalse(cursor.moveToNext())
        }

        db.query(
            """
            SELECT importSourceApp, importExternalId
            FROM blood_test_results
            WHERE uuid = 'result-seed'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun assertNullProvenanceRowsAreAllowed(db: SupportSQLiteDatabase) {
        insertMedicationLogEntry(db, uuid = "log-null-1")
        insertMedicationLogEntry(db, uuid = "log-null-2")
        insertBloodTestPanel(db, uuid = "panel-null-1")
        insertBloodTestPanel(db, uuid = "panel-null-2")
        insertBloodTestResult(
            db = db,
            uuid = "result-null-1",
            panelUuid = "panel-null-1",
            displayOrder = 1,
            builtinAnalyteKey = "ESTRADIOL",
        )
        insertBloodTestResult(
            db = db,
            uuid = "result-null-2",
            panelUuid = "panel-null-2",
            displayOrder = 1,
            builtinAnalyteKey = "ESTRADIOL",
        )
    }

    private fun assertDuplicateNonNullProvenanceIsRejected(db: SupportSQLiteDatabase) {
        insertMedicationLogEntry(
            db = db,
            uuid = "log-import-1",
            importSourceApp = "transmtf",
            importExternalId = "log-1",
        )
        assertConstraintFailure {
            insertMedicationLogEntry(
                db = db,
                uuid = "log-import-2",
                importSourceApp = "transmtf",
                importExternalId = "log-1",
            )
        }

        insertBloodTestPanel(
            db = db,
            uuid = "panel-import-1",
            importSourceApp = "oyama",
            importPanelKey = 100,
        )
        assertConstraintFailure {
            insertBloodTestPanel(
                db = db,
                uuid = "panel-import-2",
                importSourceApp = "oyama",
                importPanelKey = 100,
            )
        }

        insertBloodTestPanel(db, uuid = "panel-import-results-1")
        insertBloodTestPanel(db, uuid = "panel-import-results-2")
        insertBloodTestResult(
            db = db,
            uuid = "result-import-1",
            panelUuid = "panel-import-results-1",
            displayOrder = 1,
            builtinAnalyteKey = "TESTOSTERONE",
            importSourceApp = "nomtf",
            importExternalId = "result-1",
        )
        assertConstraintFailure {
            insertBloodTestResult(
                db = db,
                uuid = "result-import-2",
                panelUuid = "panel-import-results-2",
                displayOrder = 1,
                builtinAnalyteKey = "TESTOSTERONE",
                importSourceApp = "nomtf",
                importExternalId = "result-1",
            )
        }
    }

    private fun assertConstraintFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) {
            // Expected.
        }
    }

    private fun seedVersion6Rows(db: SupportSQLiteDatabase) {
        insertMedicine(db, uuid = "medicine-seed", identityKey = "medicine-seed-key")
        insertVersion6MedicationLogEntry(db, uuid = "log-seed")
        insertVersion6BloodTestPanel(db, uuid = "panel-seed")
        insertVersion6BloodTestResult(db, uuid = "result-seed", panelUuid = "panel-seed")
    }

    private fun insertMedicine(
        db: SupportSQLiteDatabase,
        uuid: String,
        identityKey: String,
    ) {
        db.execSQL(
            """
            INSERT INTO medicines (
                uuid, selectionKind, medicationKey, customMedicationName,
                customMedicationNameNormalized, category, preparationType,
                strengthMgPerTablet, strengthMgPerVial, concentrationMgPerMl,
                vialVolumeMl, concentrationPercent, sachetWeightGrams,
                containerWeightGrams, patchTotalMg, patchReleaseRateMcgPerDay,
                displayName, identityKey, createdAtEpochMillis, updatedAtEpochMillis,
                archivedAtEpochMillis, displayDoseUnit, trackingEnabled,
                stockUnitsRemaining, stockUnitsLastTotal, openContainerAmount,
                warnAtDaysRemaining, stockGeneration
            ) VALUES (
                '$uuid', 'CATALOG', 'estradiol', NULL, NULL, 'ESTROGEN',
                'TABLET', 1.0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                'Estradiol', '$identityKey', 1000, 1000, NULL, 'MG', 0,
                NULL, NULL, NULL, 14, 0
            )
            """.trimIndent()
        )
    }

    private fun insertMedicationLogEntry(
        db: SupportSQLiteDatabase,
        uuid: String,
        importSourceApp: String? = null,
        importExternalId: String? = null,
    ) {
        db.execSQL(
            """
            INSERT INTO medication_log_entries (
                uuid, category, medicineUuid, applicationType,
                doseInstructionKind, tabletFractionNumerator,
                tabletFractionDenominator, doseVolumeMl, doseWeightGrams,
                equivalentE2Mg, sourceGroupUuid, scheduleTimeUuid,
                appliedAtEpochMillis, appliedAtTimeZoneId, scheduledForIso,
                count, gelApplicationArea, doseAmountDelta,
                importSourceApp, importExternalId
            ) VALUES (
                '$uuid', 'ESTROGEN', NULL, 'ORAL', 'TABLET_FRACTION',
                1, 1, NULL, NULL, NULL, NULL, NULL, 1000, 'UTC',
                NULL, 1, 'DEFAULT', NULL,
                ${sqlString(importSourceApp)}, ${sqlString(importExternalId)}
            )
            """.trimIndent()
        )
    }

    private fun insertVersion6MedicationLogEntry(
        db: SupportSQLiteDatabase,
        uuid: String,
    ) {
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
                '$uuid', 'ESTROGEN', NULL, 'ORAL', 'TABLET_FRACTION',
                1, 1, NULL, NULL, NULL, NULL, NULL, 1000, 'UTC',
                NULL, 1, 'DEFAULT', NULL
            )
            """.trimIndent()
        )
    }

    private fun insertBloodTestPanel(
        db: SupportSQLiteDatabase,
        uuid: String,
        importSourceApp: String? = null,
        importPanelKey: Long? = null,
    ) {
        db.execSQL(
            """
            INSERT INTO blood_test_panels (
                uuid, collectedAtInstantEpochMillis, collectedAtTimeZoneId,
                notes, timeSinceLastEstradiolDoseMillis,
                timeSinceLastTestosteroneDoseMillis, createdAtEpochMillis,
                updatedAtEpochMillis, importSourceApp, importPanelKey
            ) VALUES (
                '$uuid', 1000, 'UTC', NULL, NULL, NULL, 1000, 1000,
                ${sqlString(importSourceApp)}, ${importPanelKey ?: "NULL"}
            )
            """.trimIndent()
        )
    }

    private fun insertVersion6BloodTestPanel(
        db: SupportSQLiteDatabase,
        uuid: String,
    ) {
        db.execSQL(
            """
            INSERT INTO blood_test_panels (
                uuid, collectedAtInstantEpochMillis, collectedAtTimeZoneId,
                notes, timeSinceLastEstradiolDoseMillis,
                timeSinceLastTestosteroneDoseMillis, createdAtEpochMillis,
                updatedAtEpochMillis
            ) VALUES (
                '$uuid', 1000, 'UTC', NULL, NULL, NULL, 1000, 1000
            )
            """.trimIndent()
        )
    }

    private fun insertBloodTestResult(
        db: SupportSQLiteDatabase,
        uuid: String,
        panelUuid: String,
        displayOrder: Int,
        builtinAnalyteKey: String,
        importSourceApp: String? = null,
        importExternalId: String? = null,
    ) {
        db.execSQL(
            """
            INSERT INTO blood_test_results (
                uuid, panelUuid, createdAtEpochMillis, displayOrder,
                builtinAnalyteKey, customAnalyteUuid, value, unitSnapshot,
                canonicalValue, importSourceApp, importExternalId
            ) VALUES (
                '$uuid', '$panelUuid', 1000, $displayOrder, '$builtinAnalyteKey',
                NULL, 100.0, 'pg/mL', 100.0,
                ${sqlString(importSourceApp)}, ${sqlString(importExternalId)}
            )
            """.trimIndent()
        )
    }

    private fun insertVersion6BloodTestResult(
        db: SupportSQLiteDatabase,
        uuid: String,
        panelUuid: String,
    ) {
        db.execSQL(
            """
            INSERT INTO blood_test_results (
                uuid, panelUuid, createdAtEpochMillis, displayOrder,
                builtinAnalyteKey, customAnalyteUuid, value, unitSnapshot,
                canonicalValue
            ) VALUES (
                '$uuid', '$panelUuid', 1000, 1, 'ESTRADIOL',
                NULL, 100.0, 'pg/mL', 100.0
            )
            """.trimIndent()
        )
    }

    private fun sqlString(value: String?): String =
        value?.let { "'$it'" } ?: "NULL"

    private fun createV6Schema(db: SupportSQLiteDatabase) {
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
                displayDoseUnit TEXT NOT NULL DEFAULT 'MG',
                trackingEnabled INTEGER NOT NULL DEFAULT 0,
                stockUnitsRemaining REAL,
                stockUnitsLastTotal REAL,
                openContainerAmount REAL,
                warnAtDaysRemaining INTEGER NOT NULL DEFAULT 14,
                stockGeneration INTEGER NOT NULL DEFAULT 0
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
                gelApplicationArea TEXT NOT NULL DEFAULT 'DEFAULT',
                doseAmountDelta REAL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX index_medication_log_entries_category_appliedAtEpochMillis
            ON medication_log_entries(category, appliedAtEpochMillis)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE blood_test_panels (
                uuid TEXT NOT NULL PRIMARY KEY,
                collectedAtInstantEpochMillis INTEGER NOT NULL,
                collectedAtTimeZoneId TEXT NOT NULL,
                notes TEXT,
                timeSinceLastEstradiolDoseMillis INTEGER,
                timeSinceLastTestosteroneDoseMillis INTEGER,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX index_blood_test_panels_collectedAtInstantEpochMillis
            ON blood_test_panels(collectedAtInstantEpochMillis)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE blood_test_results (
                uuid TEXT NOT NULL PRIMARY KEY,
                panelUuid TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                displayOrder INTEGER NOT NULL,
                builtinAnalyteKey TEXT,
                customAnalyteUuid TEXT,
                value REAL NOT NULL,
                unitSnapshot TEXT NOT NULL,
                canonicalValue REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_blood_test_results_panelUuid ON blood_test_results(panelUuid)")
        db.execSQL("CREATE INDEX index_blood_test_results_builtinAnalyteKey ON blood_test_results(builtinAnalyteKey)")
        db.execSQL("CREATE INDEX index_blood_test_results_customAnalyteUuid ON blood_test_results(customAnalyteUuid)")
        db.execSQL(
            """
            CREATE UNIQUE INDEX index_blood_test_results_panelUuid_displayOrder
            ON blood_test_results(panelUuid, displayOrder)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX index_blood_test_results_panelUuid_builtinAnalyteKey
            ON blood_test_results(panelUuid, builtinAnalyteKey)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX index_blood_test_results_panelUuid_customAnalyteUuid
            ON blood_test_results(panelUuid, customAnalyteUuid)
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
