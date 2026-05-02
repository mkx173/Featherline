package com.mkx.hrttracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

val MIGRATION_26_27: Migration = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medication_groups ADD COLUMN recreatedFromGroupUuid TEXT")
        db.execSQL(
            """
            UPDATE medication_groups
            SET recreatedFromGroupUuid = (
                SELECT original.uuid
                FROM medication_groups AS original
                WHERE original.replacedByGroupUuid = medication_groups.uuid
                ORDER BY original.updatedAtEpochMillis DESC, original.createdAtEpochMillis DESC
                LIMIT 1
            )
            WHERE EXISTS (
                SELECT 1
                FROM medication_groups AS original
                WHERE original.replacedByGroupUuid = medication_groups.uuid
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_25_26: Migration = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val zoneId = ZoneId.systemDefault()
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.execSQL("ALTER TABLE medication_groups ADD COLUMN archivedAtLocalIso TEXT")

        db.query(
            """
            SELECT uuid, archivedAtEpochMillis
            FROM medication_groups
            WHERE archivedAtEpochMillis IS NOT NULL
            """.trimIndent()
        ).use { cursor ->
            val uuidIndex = cursor.getColumnIndexOrThrow("uuid")
            val archivedAtIndex = cursor.getColumnIndexOrThrow("archivedAtEpochMillis")
            while (cursor.moveToNext()) {
                val archivedAtLocal = Instant.ofEpochMilli(cursor.getLong(archivedAtIndex))
                    .atZone(zoneId)
                    .toLocalDateTime()
                    .toString()
                db.execSQL(
                    """
                    UPDATE medication_groups
                    SET archivedAtLocalIso = ?
                    WHERE uuid = ?
                    """.trimIndent(),
                    arrayOf(archivedAtLocal, cursor.getString(uuidIndex))
                )
            }
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS medication_group_schedule_times_new (
                uuid TEXT NOT NULL PRIMARY KEY,
                groupUuid TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                hourOfDay INTEGER NOT NULL,
                minuteOfHour INTEGER NOT NULL,
                effectiveFromLocalIso TEXT NOT NULL,
                FOREIGN KEY(groupUuid) REFERENCES medication_groups(uuid) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.query(
            """
            SELECT
                times.groupUuid,
                times.sortOrder,
                times.hourOfDay,
                times.minuteOfHour,
                groups.scheduleSinceEpochDay,
                groups.createdAtEpochMillis,
                groups.includePastScheduledSlots
            FROM medication_group_schedule_times AS times
            INNER JOIN medication_groups AS groups ON groups.uuid = times.groupUuid
            ORDER BY times.groupUuid ASC, times.sortOrder ASC
            """.trimIndent()
        ).use { cursor ->
            val groupUuidIndex = cursor.getColumnIndexOrThrow("groupUuid")
            val sortOrderIndex = cursor.getColumnIndexOrThrow("sortOrder")
            val hourIndex = cursor.getColumnIndexOrThrow("hourOfDay")
            val minuteIndex = cursor.getColumnIndexOrThrow("minuteOfHour")
            val sinceIndex = cursor.getColumnIndexOrThrow("scheduleSinceEpochDay")
            val createdAtIndex = cursor.getColumnIndexOrThrow("createdAtEpochMillis")
            val includePastIndex = cursor.getColumnIndexOrThrow("includePastScheduledSlots")
            while (cursor.moveToNext()) {
                val effectiveFromLocal = if (cursor.getInt(includePastIndex) == 0) {
                    Instant.ofEpochMilli(cursor.getLong(createdAtIndex))
                        .atZone(zoneId)
                        .toLocalDateTime()
                } else {
                    LocalDate.ofEpochDay(cursor.getLong(sinceIndex)).atStartOfDay()
                }
                db.execSQL(
                    """
                    INSERT INTO medication_group_schedule_times_new (
                        uuid,
                        groupUuid,
                        sortOrder,
                        hourOfDay,
                        minuteOfHour,
                        effectiveFromLocalIso
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        UUID.randomUUID().toString(),
                        cursor.getString(groupUuidIndex),
                        cursor.getInt(sortOrderIndex),
                        cursor.getInt(hourIndex),
                        cursor.getInt(minuteIndex),
                        effectiveFromLocal.toString(),
                    )
                )
            }
        }

        db.execSQL("DROP TABLE medication_group_schedule_times")
        db.execSQL(
            """
            ALTER TABLE medication_group_schedule_times_new
            RENAME TO medication_group_schedule_times
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_medication_group_schedule_times_groupUuid
            ON medication_group_schedule_times(groupUuid)
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE medication_log_entries ADD COLUMN scheduleTimeUuid TEXT")
        db.execSQL(
            """
            UPDATE medication_log_entries
            SET scheduleTimeUuid = (
                SELECT times.uuid
                FROM medication_group_schedule_times AS times
                WHERE times.groupUuid = medication_log_entries.sourceGroupUuid
                  AND printf('%02d:%02d', times.hourOfDay, times.minuteOfHour) =
                      substr(medication_log_entries.scheduledForIso, 12, 5)
                  AND (
                      SELECT COUNT(*)
                      FROM medication_group_schedule_times AS matchingTimes
                      WHERE matchingTimes.groupUuid = times.groupUuid
                        AND matchingTimes.hourOfDay = times.hourOfDay
                        AND matchingTimes.minuteOfHour = times.minuteOfHour
                  ) = 1
                LIMIT 1
            )
            WHERE sourceGroupUuid IS NOT NULL
              AND scheduledForIso IS NOT NULL
            """.trimIndent()
        )
        db.execSQL("PRAGMA foreign_keys=ON")
    }
}

val MIGRATION_24_25: Migration = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE medication_groups ADD COLUMN replacedByGroupUuid TEXT"
        )
    }
}

val MIGRATION_23_24: Migration = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE medication_groups ADD COLUMN includePastScheduledSlots INTEGER NOT NULL DEFAULT 1"
        )
    }
}

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
