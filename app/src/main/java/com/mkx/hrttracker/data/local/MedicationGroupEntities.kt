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
    val colorKey: String,
    val notificationsEnabled: Boolean = false,
    val scheduleType: String,
    val scheduleInterval: Int,
    val scheduleSinceEpochDay: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null,
    val includePastScheduledSlots: Boolean = true,
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
    val count: Int,
    val category: String,
    val applicationType: String,
    val selectionKind: String,
    val medicationKey: String?,
    val customMedicationName: String?,
    val doseKind: String,
    val doseValueMg: Double?,
    val customDoseUnit: String = "MG",
    val doseValuePercent: Double?,
    val doseWeightGrams: Double?,
    val doseReleaseRateMcgPerDay: Double?,
    val gelApplicationArea: String = "DEFAULT",
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

@Entity(
    tableName = "medication_group_weekly_days",
    primaryKeys = ["groupUuid", "dayOfWeek"],
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
data class MedicationGroupWeeklyDayEntity(
    val groupUuid: String,
    val dayOfWeek: Int,
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
    @Relation(
        parentColumn = "uuid",
        entityColumn = "groupUuid"
    )
    val weeklyDays: List<MedicationGroupWeeklyDayEntity>,
)
