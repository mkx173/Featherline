package com.mkx.hrttracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MedicationLogEntryEntity::class,
        MedicationGroupEntity::class,
        MedicationGroupItemEntity::class,
        MedicationGroupScheduleTimeEntity::class
    ],
    version = 7,
    exportSchema = false,
)
abstract class HrtTrackerDatabase : RoomDatabase() {
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun medicationGroupDao(): MedicationGroupDao
}
