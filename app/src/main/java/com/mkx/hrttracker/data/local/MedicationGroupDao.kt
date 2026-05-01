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

    @Transaction
    @Query(
        """
        SELECT * FROM medication_groups
        ORDER BY updatedAtEpochMillis DESC, createdAtEpochMillis DESC
        """
    )
    suspend fun getGroups(): List<MedicationGroupWithItemsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: MedicationGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<MedicationGroupEntity>)

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

    @Query(
        """
        DELETE FROM medication_group_weekly_days
        WHERE groupUuid = :groupUuid
        """
    )
    suspend fun deleteWeeklyDaysForGroup(groupUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MedicationGroupItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleTimes(scheduleTimes: List<MedicationGroupScheduleTimeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyDays(weeklyDays: List<MedicationGroupWeeklyDayEntity>)

    @Query(
        """
        DELETE FROM medication_groups
        WHERE uuid = :uuid
        """
    )
    suspend fun deleteGroup(uuid: String)

    @Query(
        """
        UPDATE medication_groups
        SET archivedAtEpochMillis = :archivedAtEpochMillis,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun updateGroupArchiveState(
        uuid: String,
        archivedAtEpochMillis: Long?,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE medication_groups
        SET replacedByGroupUuid = :replacedByGroupUuid,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun updateGroupReplacedBy(
        uuid: String,
        replacedByGroupUuid: String?,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE medication_groups
        SET updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE uuid = :uuid
        """
    )
    suspend fun updateGroupUpdatedAt(
        uuid: String,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        DELETE FROM medication_groups
        """
    )
    suspend fun deleteAllGroups()

    @Transaction
    suspend fun upsertGroupWithItems(
        group: MedicationGroupEntity,
        items: List<MedicationGroupItemEntity>,
        scheduleTimes: List<MedicationGroupScheduleTimeEntity>,
        weeklyDays: List<MedicationGroupWeeklyDayEntity>
    ) {
        insertGroup(group)
        deleteItemsForGroup(group.uuid)
        deleteScheduleTimesForGroup(group.uuid)
        deleteWeeklyDaysForGroup(group.uuid)
        if (items.isNotEmpty()) {
            insertItems(items)
        }
        if (scheduleTimes.isNotEmpty()) {
            insertScheduleTimes(scheduleTimes)
        }
        if (weeklyDays.isNotEmpty()) {
            insertWeeklyDays(weeklyDays)
        }
    }
}
