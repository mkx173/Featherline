package com.mkx.hrttracker.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.util.UUID

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
    val archivedAtLocalIso: String? = null,
    val includePastScheduledSlots: Boolean = true,
    val replacedByGroupUuid: String? = null,
    val recreatedFromGroupUuid: String? = null,
    val autoAddFutureEntries: Boolean = false,
)

@Entity(
    tableName = "medication_group_items",
    foreignKeys = [
        ForeignKey(
            entity = MedicationGroupEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["groupUuid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["medicineUuid"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [Index("groupUuid"), Index("medicineUuid")]
)
data class MedicationGroupItemEntity(
    @PrimaryKey val uuid: String,
    val groupUuid: String,
    val sortOrder: Int,
    val count: Int,
    val medicineUuid: String?,
    val applicationType: String,
    val doseInstructionKind: String,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
    val gelApplicationArea: String = "DEFAULT",
)

@Entity(
    tableName = "medication_group_schedule_times",
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
    val effectiveFromLocalIso: String = LocalDate.of(1970, 1, 1).atStartOfDay().toString(),
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
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
