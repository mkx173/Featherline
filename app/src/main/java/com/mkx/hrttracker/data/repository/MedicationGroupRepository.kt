package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationGroupRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder
) {
    fun observeGroups(): Flow<List<MedicationGroup>> {
        return databaseHolder.get().medicationGroupDao().observeGroups()
            .map { groups -> groups.map { group -> group.toModel() } }
    }

    suspend fun getGroup(uuid: UUID): MedicationGroup? {
        return databaseHolder.get().medicationGroupDao().getGroup(uuid.toString())?.toModel()
    }

    suspend fun saveGroup(
        uuid: UUID?,
        name: String,
        medications: List<MedicationGroupMedicationInput>
    ) {
        val dao = databaseHolder.get().medicationGroupDao()
        val nowEpochMillis = Instant.now().toEpochMilli()
        val groupUuid = uuid ?: UUID.randomUUID()
        val existingGroup = uuid?.let { dao.getGroup(it.toString()) }
        val createdAtEpochMillis = existingGroup?.group?.createdAtEpochMillis ?: nowEpochMillis

        dao.upsertGroupWithItems(
            group = MedicationGroupEntity(
                uuid = groupUuid.toString(),
                name = name,
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
            }
        )
    }

    private fun MedicationGroupWithItemsEntity.toModel(): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString(group.uuid),
            name = group.name,
            medications = items.sortedBy(MedicationGroupItemEntity::sortOrder).map { item ->
                MedicationGroupMedication(
                    uuid = UUID.fromString(item.uuid),
                    routeOfAdministration = RouteOfAdministration.fromStorageValue(item.routeOfAdministration),
                    medicineName = item.medicineName,
                    dosageMgAsMedicine = item.dosageMgAsMedicine,
                )
            },
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
