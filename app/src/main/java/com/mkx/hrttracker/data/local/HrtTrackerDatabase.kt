package com.mkx.hrttracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

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
    version = 1,
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
