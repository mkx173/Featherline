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
        E2CalibrationMetadataEntity::class,
        PkCalibrationDisplayArtifactEntity::class,
        TrackedDateEntity::class,
        NoteEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class HrtTrackerDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun medicationGroupDao(): MedicationGroupDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bloodTestDao(): BloodTestDao
    abstract fun pkCalibrationDao(): PkCalibrationDao
    abstract fun homeDao(): HomeDao
    abstract fun journalDao(): JournalDao
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

internal val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tracked_dates (
                uuid TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                iconKey TEXT NOT NULL,
                dateIso TEXT NOT NULL,
                paletteKey TEXT,
                pinnedOrder INTEGER,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_tracked_dates_pinnedOrder ON tracked_dates(pinnedOrder)")
        db.execSQL("CREATE INDEX index_tracked_dates_dateIso ON tracked_dates(dateIso)")
        db.execSQL(
            """
            CREATE TABLE notes (
                uuid TEXT NOT NULL PRIMARY KEY,
                dateIso TEXT NOT NULL,
                text TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX index_notes_dateIso ON notes(dateIso)")
    }
}

// v8 -> v9: adds nullable `heroBackgroundKey` to `tracked_dates`. Rows that
// pre-date this column default to NULL, which the mapper now reads as the Date
// color background default.
internal val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracked_dates ADD COLUMN heroBackgroundKey TEXT")
    }
}

// v9 -> v10: result-owned review metadata plus the one derived display artifact.
// Both tables intentionally start empty. The rev-8.6 route-calibration design never
// shipped durable thetaS/routeExposureScale or legacy disposition state in this app,
// so there is no old calibration value to delete or map during this migration.
// Existing installs therefore remain population-safe until a result is explicitly
// reviewed and a display artifact is recomputed under the current contracts.
internal val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v10 as originally shipped: digest-bound acceptance columns. The v10 A2
        // refactor renamed them; that rename is MIGRATION_10_11, never this one.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `e2_calibration_metadata` (
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
            CREATE TABLE IF NOT EXISTS `pk_calibration_display_artifact` (
                `singletonId` INTEGER NOT NULL,
                `homeSnapshotGeneration` INTEGER NOT NULL,
                `schema` TEXT NOT NULL,
                `calibrationModelVersion` TEXT NOT NULL,
                `resultInputDigestSchema` TEXT NOT NULL,
                `resultInputDigestAlgorithm` TEXT NOT NULL,
                `resultInputDigestHexLower` TEXT NOT NULL,
                `promotedRouteStableIds` TEXT NOT NULL,
                `injectionLogScale` REAL,
                `patchLogScale` REAL,
                `gelLogScale` REAL,
                `oralLogScale` REAL,
                `sublingualLogScale` REAL,
                PRIMARY KEY(`singletonId`)
            )
            """.trimIndent()
        )
    }
}

// v10 → v11: repairs the v10 A2 refactor's in-place rewrite of
// `e2_calibration_metadata`, which replaced the digest-bound acceptance columns
// with the model §A2 staleness record while the version stayed at 10. Two v10
// shapes therefore exist on branch-era installs; this rebuild accepts either.
// Digest-bound acceptances cannot be honored under the attestation model, so
// those rows fall back to AUTO (the outlier returns to review); exclusions and
// record-carrying acceptances are preserved.
internal val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        var hasDigestColumns = false
        db.query("PRAGMA table_info(`e2_calibration_metadata`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "acceptedReviewDigestSchema") {
                    hasDigestColumns = true
                }
            }
        }
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `e2_calibration_metadata_v11` (
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
        if (hasDigestColumns) {
            db.execSQL(
                """
                INSERT INTO `e2_calibration_metadata_v11`
                    (`resultUuid`, `disposition`, `acceptedModelVersion`,
                     `acceptedSourceValueBits`, `acceptedCollectedAtEpochMillis`,
                     `updatedAtEpochMillis`)
                SELECT `resultUuid`,
                       CASE `disposition` WHEN 'ACCEPTED' THEN 'AUTO' ELSE `disposition` END,
                       NULL, NULL, NULL,
                       `updatedAtEpochMillis`
                FROM `e2_calibration_metadata`
                """.trimIndent()
            )
        } else {
            db.execSQL(
                """
                INSERT INTO `e2_calibration_metadata_v11`
                    (`resultUuid`, `disposition`, `acceptedModelVersion`,
                     `acceptedSourceValueBits`, `acceptedCollectedAtEpochMillis`,
                     `updatedAtEpochMillis`)
                SELECT `resultUuid`, `disposition`, `acceptedModelVersion`,
                       `acceptedSourceValueBits`, `acceptedCollectedAtEpochMillis`,
                       `updatedAtEpochMillis`
                FROM `e2_calibration_metadata`
                """.trimIndent()
            )
        }
        db.execSQL("DROP TABLE `e2_calibration_metadata`")
        db.execSQL(
            "ALTER TABLE `e2_calibration_metadata_v11` RENAME TO `e2_calibration_metadata`"
        )
    }
}
