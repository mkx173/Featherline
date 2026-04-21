package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWeeklyDayEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationGroupRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    @AppScope appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val groupsFlow: StateFlow<List<MedicationGroup>?> =
        databaseHolder.databaseFlow
            .flatMapLatest { database ->
                if (database == null) {
                    flowOf<List<MedicationGroup>?>(null)
                } else {
                    database.medicationGroupDao().observeGroups()
                        .map<List<MedicationGroupWithItemsEntity>, List<MedicationGroup>?> { groups ->
                            groups.map { it.toModel() }
                        }
                        .catch { emit(emptyList()) }
                }
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    fun observeGroups(): Flow<List<MedicationGroup>?> = groupsFlow

    suspend fun getGroups(): List<MedicationGroup> {
        return databaseHolder.get().medicationGroupDao().getGroups().map { it.toModel() }
    }

    suspend fun getGroup(uuid: UUID): MedicationGroup? {
        return databaseHolder.get().medicationGroupDao().getGroup(uuid.toString())?.toModel()
    }

    suspend fun deleteGroup(uuid: UUID) {
        val database = databaseHolder.get()
        val groupUuid = uuid.toString()
        database.withTransaction {
            database.medicationLogDao().reclassifyEntriesForDeletedGroup(
                groupUuid = groupUuid,
                manualSourceType = MedicationLogEntrySourceType.MANUAL.name
            )
            database.medicationGroupDao().deleteGroup(groupUuid)
        }
    }

    suspend fun saveGroup(
        uuid: UUID?,
        name: String,
        schedule: MedicationGroupScheduleInput,
        medications: List<MedicationGroupMedicationInput>,
        notificationsEnabled: Boolean = false
    ): UUID {
        val dao = databaseHolder.get().medicationGroupDao()
        val nowEpochMillis = Instant.now().toEpochMilli()
        val groupUuid = uuid ?: UUID.randomUUID()
        val existingGroup = uuid?.let { dao.getGroup(it.toString()) }
        val createdAtEpochMillis = existingGroup?.group?.createdAtEpochMillis ?: nowEpochMillis

        dao.upsertGroupWithItems(
            group = MedicationGroupEntity(
                uuid = groupUuid.toString(),
                name = name,
                notificationsEnabled = notificationsEnabled,
                scheduleType = schedule.type.name,
                scheduleInterval = schedule.interval,
                scheduleSinceEpochDay = schedule.since.toEpochDay(),
                weeklyDayOfWeek = schedule.weeklyDayOfWeek?.value,
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = nowEpochMillis
            ),
            items = medications.mapIndexed { index, medication ->
                MedicationGroupItemEntity(
                    uuid = (medication.uuid ?: UUID.randomUUID()).toString(),
                    groupUuid = groupUuid.toString(),
                    sortOrder = index,
                    routeOfAdministration = medication.routeOfAdministration.name,
                    medicineName = medication.medicineName,
                    dosageMgAsMedicine = medication.dosageMgAsMedicine,
                )
            },
            scheduleTimes = schedule.times.mapIndexed { index, time ->
                MedicationGroupScheduleTimeEntity(
                    groupUuid = groupUuid.toString(),
                    sortOrder = index,
                    hourOfDay = time.hour,
                    minuteOfHour = time.minute
                )
            },
            weeklyDays = schedule.weeklyDaysOfWeek
                .sortedBy { it.value }
                .map { dayOfWeek ->
                    MedicationGroupWeeklyDayEntity(
                        groupUuid = groupUuid.toString(),
                        dayOfWeek = dayOfWeek.value
                    )
                }
        )

        return groupUuid
    }

    private fun MedicationGroupWithItemsEntity.toModel(): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString(group.uuid),
            name = group.name,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.fromStorageValue(group.scheduleType),
                interval = group.scheduleInterval,
                since = LocalDate.ofEpochDay(group.scheduleSinceEpochDay),
                weeklyDaysOfWeek = weeklyDays
                    .map { weeklyDay -> DayOfWeek.of(weeklyDay.dayOfWeek) }
                    .toSet()
                    .ifEmpty {
                        group.weeklyDayOfWeek?.let(DayOfWeek::of)?.let(::setOf).orEmpty()
                    },
                times = scheduleTimes.sortedBy(MedicationGroupScheduleTimeEntity::sortOrder).map { time ->
                    LocalTime.of(time.hourOfDay, time.minuteOfHour)
                }
            ),
            medications = items.sortedBy(MedicationGroupItemEntity::sortOrder).map { item ->
                MedicationGroupMedication(
                    uuid = UUID.fromString(item.uuid),
                    routeOfAdministration = RouteOfAdministration.fromStorageValue(item.routeOfAdministration),
                    medicineName = item.medicineName,
                    dosageMgAsMedicine = item.dosageMgAsMedicine,
                )
            },
            notificationsEnabled = group.notificationsEnabled,
            createdAt = Instant.ofEpochMilli(group.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(group.updatedAtEpochMillis)
        )
    }
}

data class MedicationGroupMedicationInput(
    val uuid: UUID? = null,
    val routeOfAdministration: RouteOfAdministration,
    val medicineName: String,
    val dosageMgAsMedicine: Double,
)

data class MedicationGroupScheduleInput(
    val type: MedicationGroupScheduleType,
    val interval: Int,
    val since: LocalDate,
    val weeklyDaysOfWeek: Set<DayOfWeek>,
    val times: List<LocalTime>,
) {
    constructor(
        type: MedicationGroupScheduleType,
        interval: Int,
        since: LocalDate,
        weeklyDayOfWeek: DayOfWeek?,
        times: List<LocalTime>,
    ) : this(
        type = type,
        interval = interval,
        since = since,
        weeklyDaysOfWeek = weeklyDayOfWeek?.let(::setOf).orEmpty(),
        times = times,
    )

    val weeklyDayOfWeek: DayOfWeek?
        get() = weeklyDaysOfWeek.sortedBy { it.value }.firstOrNull()
}
