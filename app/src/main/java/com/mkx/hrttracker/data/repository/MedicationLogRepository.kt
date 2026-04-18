package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationLogRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    @AppScope appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val entriesFlow: StateFlow<List<MedicationLogEntry>?> =
        databaseHolder.databaseFlow
            .flatMapLatest { database ->
                if (database == null) {
                    flowOf<List<MedicationLogEntry>?>(null)
                } else {
                    database.medicationLogDao().observeEntries()
                        .map<List<MedicationLogEntryEntity>, List<MedicationLogEntry>?> { entries ->
                            entries.map { it.toModel() }
                        }
                        .catch { emit(emptyList()) }
                }
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    fun observeEntries(): Flow<List<MedicationLogEntry>?> = entriesFlow

    suspend fun getEntry(uuid: UUID): MedicationLogEntry? {
        return databaseHolder.get().medicationLogDao().getEntry(uuid.toString())?.toModel()
    }

    suspend fun deleteEntries(uuids: Collection<UUID>) {
        if (uuids.isEmpty()) {
            return
        }

        databaseHolder.get().medicationLogDao().deleteEntries(uuids.map(UUID::toString))
    }

    suspend fun saveEntry(
        uuid: UUID?,
        routeOfAdministration: RouteOfAdministration,
        medicineName: String,
        dosageMgAsMedicine: Double,
        sourceType: MedicationLogEntrySourceType,
        sourceGroupUuid: UUID?,
        appliedAt: Instant
    ) {
        databaseHolder.get().medicationLogDao().insertEntry(
            MedicationLogEntryEntity(
                uuid = (uuid ?: UUID.randomUUID()).toString(),
                routeOfAdministration = routeOfAdministration.name,
                medicineName = medicineName,
                dosageMgAsMedicine = dosageMgAsMedicine,
                dosageMgAsEstradiol = EstradiolEquivalentCalculator.calculate(
                    medicineName = medicineName,
                    dosageMgAsMedicine = dosageMgAsMedicine
                ),
                sourceType = sourceType.name,
                sourceGroupUuid = sourceGroupUuid?.toString(),
                appliedAtEpochMillis = appliedAt.toEpochMilli()
            )
        )
    }

    private fun MedicationLogEntryEntity.toModel(): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.fromString(uuid),
            routeOfAdministration = RouteOfAdministration.fromStorageValue(routeOfAdministration),
            medicineName = medicineName,
            dosageMgAsMedicine = dosageMgAsMedicine,
            dosageMgAsEstradiol = dosageMgAsEstradiol,
            sourceType = MedicationLogEntrySourceType.fromStorageValue(sourceType),
            sourceGroupUuid = sourceGroupUuid?.let(UUID::fromString),
            appliedAt = Instant.ofEpochMilli(appliedAtEpochMillis)
        )
    }
}
