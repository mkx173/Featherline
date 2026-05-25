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
    version = 3,
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
