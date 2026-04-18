package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.util.UUID

data class MedicationGroup(
    val uuid: UUID,
    val name: String,
    val medications: List<MedicationGroupMedication>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MedicationGroupMedication(
    val uuid: UUID,
    val routeOfAdministration: RouteOfAdministration,
    val medicineName: String,
    val dosageMgAsMedicine: Double,
)
