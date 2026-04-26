package com.mkx.hrttracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_22_23: Migration = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS medication_log_entries_new (
                uuid TEXT NOT NULL PRIMARY KEY,
                category TEXT NOT NULL,
                applicationType TEXT NOT NULL,
                selectionKind TEXT NOT NULL,
                medicationKey TEXT,
                customMedicationName TEXT,
                doseKind TEXT NOT NULL,
                doseValueMg REAL,
                customDoseUnit TEXT NOT NULL DEFAULT 'MG',
                doseValuePercent REAL,
                doseWeightGrams REAL,
                doseReleaseRateMcgPerDay REAL,
                dosageMgAsEstradiol REAL,
                sourceGroupUuid TEXT,
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
                uuid,
                category,
                applicationType,
                selectionKind,
                medicationKey,
                customMedicationName,
                doseKind,
                doseValueMg,
                customDoseUnit,
                doseValuePercent,
                doseWeightGrams,
                doseReleaseRateMcgPerDay,
                dosageMgAsEstradiol,
                sourceGroupUuid,
                appliedAtEpochMillis,
                appliedAtTimeZoneId,
                scheduledForIso,
                count,
                gelApplicationArea
            )
            SELECT
                uuid,
                category,
                applicationType,
                selectionKind,
                medicationKey,
                customMedicationName,
                doseKind,
                doseValueMg,
                customDoseUnit,
                doseValuePercent,
                doseWeightGrams,
                doseReleaseRateMcgPerDay,
                dosageMgAsEstradiol,
                sourceGroupUuid,
                appliedAtEpochMillis,
                appliedAtTimeZoneId,
                scheduledForIso,
                count,
                gelApplicationArea
            FROM medication_log_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE medication_log_entries")
        db.execSQL("ALTER TABLE medication_log_entries_new RENAME TO medication_log_entries")
        db.execSQL("PRAGMA foreign_keys=ON")
    }
}

val MIGRATION_21_22: Migration = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE medication_group_items ADD COLUMN customDoseUnit TEXT NOT NULL DEFAULT 'MG'"
        )
        db.execSQL(
            "ALTER TABLE medication_log_entries ADD COLUMN customDoseUnit TEXT NOT NULL DEFAULT 'MG'"
        )
    }
}

val MIGRATION_20_21: Migration = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE medication_groups ADD COLUMN archivedAtEpochMillis INTEGER"
        )
        db.execSQL(
            "ALTER TABLE medication_group_items ADD COLUMN gelApplicationArea TEXT NOT NULL DEFAULT 'DEFAULT'"
        )
        db.execSQL(
            "ALTER TABLE medication_log_entries ADD COLUMN gelApplicationArea TEXT NOT NULL DEFAULT 'DEFAULT'"
        )
    }
}

val MIGRATION_19_20: Migration = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_blood_analytes_new (
                uuid TEXT NOT NULL PRIMARY KEY,
                abbreviation TEXT NOT NULL,
                name TEXT NOT NULL,
                normalizedName TEXT NOT NULL,
                unitLabel TEXT NOT NULL,
                normalizedUnitLabel TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                archivedAtEpochMillis INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO custom_blood_analytes_new (
                uuid,
                abbreviation,
                name,
                normalizedName,
                unitLabel,
                normalizedUnitLabel,
                createdAtEpochMillis,
                updatedAtEpochMillis,
                archivedAtEpochMillis
            )
            SELECT
                uuid,
                name,
                name,
                normalizedName,
                unitLabel,
                normalizedUnitLabel,
                createdAtEpochMillis,
                updatedAtEpochMillis,
                archivedAtEpochMillis
            FROM custom_blood_analytes
            """.trimIndent()
        )
        db.execSQL("DROP TABLE custom_blood_analytes")
        db.execSQL(
            "ALTER TABLE custom_blood_analytes_new RENAME TO custom_blood_analytes"
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                index_custom_blood_analytes_normalizedName_normalizedUnitLabel
            ON custom_blood_analytes(normalizedName, normalizedUnitLabel)
            """.trimIndent()
        )
        db.execSQL("PRAGMA foreign_keys=ON")
    }
}
