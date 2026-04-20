package com.mkx.hrttracker.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "medication_groups")
data class MedicationGroupEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val notificationsEnabled: Boolean = false,
    val scheduleType: String,
    val scheduleInterval: Int,
    val scheduleSinceEpochDay: Long,
    val weeklyDayOfWeek: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "medication_group_items",
    foreignKeys = [
        ForeignKey(
            entity = MedicationGroupEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["groupUuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupUuid")]
)
data class MedicationGroupItemEntity(
    @PrimaryKey val uuid: String,
    val groupUuid: String,
    val sortOrder: Int,
    val routeOfAdministration: String,
    val medicineName: String,
    val dosageMgAsMedicine: Double,
)

@Entity(
    tableName = "medication_group_schedule_times",
    primaryKeys = ["groupUuid", "sortOrder"],
    foreignKeys = [
        ForeignKey(
            entity = MedicationGroupEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["groupUuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupUuid")]
)
data class MedicationGroupScheduleTimeEntity(
    val groupUuid: String,
    val sortOrder: Int,
    val hourOfDay: Int,
    val minuteOfHour: Int,
)

data class MedicationGroupWithItemsEntity(
    @Embedded val group: MedicationGroupEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "groupUuid"
    )
    val items: List<MedicationGroupItemEntity>,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "groupUuid"
    )
    val scheduleTimes: List<MedicationGroupScheduleTimeEntity>,
)
