package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {
    @Query(
        """
        SELECT * FROM medication_log_entries
        ORDER BY appliedAtEpochMillis DESC
        """
    )
    fun observeEntries(): Flow<List<MedicationLogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MedicationLogEntryEntity)
}
