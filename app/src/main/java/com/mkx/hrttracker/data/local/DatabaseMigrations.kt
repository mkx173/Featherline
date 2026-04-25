package com.mkx.hrttracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
