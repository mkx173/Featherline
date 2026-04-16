package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.util.UUID

enum class RouteOfAdministration(val displayName: String) {
    ORAL("Oral"),
    SUBLINGUAL("Sublingual"),
    TRANSDERMAL("Transdermal"),
    INTRAMUSCULAR("Intramuscular"),
    SUBCUTANEOUS("Subcutaneous"),
    TOPICAL("Topical"),
    OTHER("Other");

    companion object {
        fun fromStorageValue(value: String?): RouteOfAdministration {
            return entries.firstOrNull { it.name == value } ?: OTHER
        }
    }
}

data class MedicationLogEntry(
    val uuid: UUID,
    val routeOfAdministration: RouteOfAdministration,
    val medicineName: String,
    val dosageMgAsMedicine: Double,
    val dosageMgAsEstradiol: Double?,
    val appliedAt: Instant,
)
