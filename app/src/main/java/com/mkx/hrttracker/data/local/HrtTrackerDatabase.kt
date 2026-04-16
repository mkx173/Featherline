package com.mkx.hrttracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MedicationLogEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HrtTrackerDatabase : RoomDatabase() {
    abstract fun medicationLogDao(): MedicationLogDao
}
