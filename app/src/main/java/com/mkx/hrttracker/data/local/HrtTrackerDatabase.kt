package com.mkx.hrttracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MedicineEntity::class,
        MedicationLogEntryEntity::class,
        MedicationGroupEntity::class,
        MedicationGroupItemEntity::class,
        MedicationGroupScheduleTimeEntity::class,
        MedicationGroupWeeklyDayEntity::class,
        UserProfileEntity::class,
        BloodTestPanelEntity::class,
        BloodTestResultEntity::class,
        CustomBloodAnalyteEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class HrtTrackerDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun medicationGroupDao(): MedicationGroupDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bloodTestDao(): BloodTestDao
    abstract fun homeDao(): HomeDao
}

// v1 → v2: adds `displayDoseUnit` to `medicines`. Rows that pre-date this
// column default to `MG`, which matches the model default and matches existing
// behavior (all medicines were displayed in mg before the picker existed).
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE medicines ADD COLUMN displayDoseUnit TEXT NOT NULL DEFAULT 'MG'"
        )
    }
}

internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // medicines: stock state + session generation
        db.execSQL("ALTER TABLE medicines ADD COLUMN trackingEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE medicines ADD COLUMN stockUnitsRemaining REAL")
        db.execSQL("ALTER TABLE medicines ADD COLUMN stockUnitsLastTotal REAL")
        db.execSQL("ALTER TABLE medicines ADD COLUMN openContainerAmount REAL")
        db.execSQL("ALTER TABLE medicines ADD COLUMN warnAtDaysRemaining INTEGER NOT NULL DEFAULT 14")
        db.execSQL("ALTER TABLE medicines ADD COLUMN stockGeneration INTEGER NOT NULL DEFAULT 0")

        // medication_log_entries: per-log deduction marker + session token
        db.execSQL("ALTER TABLE medication_log_entries ADD COLUMN stockDeductionUnits REAL")
        db.execSQL("ALTER TABLE medication_log_entries ADD COLUMN stockGeneration INTEGER")
    }
}

internal val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE medication_log_entries_new (
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
        db.execSQL(
            """
            INSERT INTO medication_log_entries_new (
                uuid, category, medicineUuid, applicationType,
                doseInstructionKind, tabletFractionNumerator,
                tabletFractionDenominator, doseVolumeMl, doseWeightGrams,
                equivalentE2Mg, sourceGroupUuid, scheduleTimeUuid,
                appliedAtEpochMillis, appliedAtTimeZoneId, scheduledForIso,
                count, gelApplicationArea
            )
            SELECT
                uuid, category, medicineUuid, applicationType,
                doseInstructionKind, tabletFractionNumerator,
                tabletFractionDenominator, doseVolumeMl, doseWeightGrams,
                equivalentE2Mg, sourceGroupUuid, scheduleTimeUuid,
                appliedAtEpochMillis, appliedAtTimeZoneId, scheduledForIso,
                count, gelApplicationArea
            FROM medication_log_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE medication_log_entries")
        db.execSQL("ALTER TABLE medication_log_entries_new RENAME TO medication_log_entries")
    }
}

internal val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE medication_log_entries ADD COLUMN doseAmountDelta REAL"
        )
    }
}

internal val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_medication_log_entries_category_appliedAtEpochMillis
            ON medication_log_entries(category, appliedAtEpochMillis)
            """.trimIndent()
        )
    }
}

internal val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medicines ADD COLUMN importedFromExternalTracker INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE medication_log_entries ADD COLUMN importSourceApp TEXT")
        db.execSQL("ALTER TABLE medication_log_entries ADD COLUMN importExternalId TEXT")
        db.execSQL("ALTER TABLE blood_test_panels ADD COLUMN importSourceApp TEXT")
        db.execSQL("ALTER TABLE blood_test_panels ADD COLUMN importPanelKey INTEGER")
        db.execSQL("ALTER TABLE blood_test_results ADD COLUMN importSourceApp TEXT")
        db.execSQL("ALTER TABLE blood_test_results ADD COLUMN importExternalId TEXT")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_medication_log_entries_importSourceApp_importExternalId
            ON medication_log_entries(importSourceApp, importExternalId)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_blood_test_panels_importSourceApp_importPanelKey
            ON blood_test_panels(importSourceApp, importPanelKey)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_blood_test_results_importSourceApp_importExternalId
            ON blood_test_results(importSourceApp, importExternalId)
            """.trimIndent()
        )
    }
}
