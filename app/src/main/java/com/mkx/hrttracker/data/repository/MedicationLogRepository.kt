package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationLogRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder
) {
    fun observeEntries(): Flow<List<MedicationLogEntry>> {
        return databaseHolder.get().medicationLogDao().observeEntries()
            .map { entries -> entries.map { entry -> entry.toModel() } }
    }

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
            appliedAt = Instant.ofEpochMilli(appliedAtEpochMillis)
        )
    }
}
