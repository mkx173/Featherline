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

    @Query(
        """
        SELECT * FROM medication_log_entries
        WHERE uuid = :uuid
        LIMIT 1
        """
    )
    suspend fun getEntry(uuid: String): MedicationLogEntryEntity?

    @Query(
        """
        DELETE FROM medication_log_entries
        WHERE uuid IN (:uuids)
        """
    )
    suspend fun deleteEntries(uuids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MedicationLogEntryEntity)
}
