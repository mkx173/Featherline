package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationGroupDao {
    @Transaction
    @Query(
        """
        SELECT * FROM medication_groups
        ORDER BY updatedAtEpochMillis DESC, createdAtEpochMillis DESC
        """
    )
    fun observeGroups(): Flow<List<MedicationGroupWithItemsEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM medication_groups
        WHERE uuid = :uuid
        LIMIT 1
        """
    )
    suspend fun getGroup(uuid: String): MedicationGroupWithItemsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: MedicationGroupEntity)

    @Query(
        """
        DELETE FROM medication_group_items
        WHERE groupUuid = :groupUuid
        """
    )
    suspend fun deleteItemsForGroup(groupUuid: String)

    @Query(
        """
        DELETE FROM medication_group_schedule_times
        WHERE groupUuid = :groupUuid
        """
    )
    suspend fun deleteScheduleTimesForGroup(groupUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MedicationGroupItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleTimes(scheduleTimes: List<MedicationGroupScheduleTimeEntity>)

    @Query(
        """
        DELETE FROM medication_groups
        WHERE uuid = :uuid
        """
    )
    suspend fun deleteGroup(uuid: String)

    @Transaction
    suspend fun upsertGroupWithItems(
        group: MedicationGroupEntity,
        items: List<MedicationGroupItemEntity>,
        scheduleTimes: List<MedicationGroupScheduleTimeEntity>
    ) {
        insertGroup(group)
        deleteItemsForGroup(group.uuid)
        deleteScheduleTimesForGroup(group.uuid)
        if (items.isNotEmpty()) {
            insertItems(items)
        }
        if (scheduleTimes.isNotEmpty()) {
            insertScheduleTimes(scheduleTimes)
        }
    }
}
