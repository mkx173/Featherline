package com.mkx.hrttracker.model.medication

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

enum class RouteOfAdministration(@get:StringRes val labelRes: Int) {
    ORAL(R.string.route_oral),
    SUBLINGUAL(R.string.route_sublingual),
    TRANSDERMAL(R.string.route_transdermal),
    INTRAMUSCULAR(R.string.route_intramuscular),
    SUBCUTANEOUS(R.string.route_subcutaneous),
    TOPICAL(R.string.route_topical),
    OTHER(R.string.route_other);

    companion object {
        fun fromStorageValue(value: String?): RouteOfAdministration {
            return entries.firstOrNull { it.name == value } ?: OTHER
        }
    }
}

enum class MedicationLogEntrySourceType {
    MANUAL,
    GROUP_MANUAL;

    companion object {
        fun fromStorageValue(value: String?): MedicationLogEntrySourceType {
            return entries.firstOrNull { it.name == value } ?: MANUAL
        }
    }
}

data class MedicationLogEntry(
    val uuid: UUID,
    val routeOfAdministration: RouteOfAdministration,
    val medicineName: String,
    val dosageMgAsMedicine: Double,
    val dosageMgAsEstradiol: Double?,
    val sourceType: MedicationLogEntrySourceType,
    val sourceGroupUuid: UUID?,
    val appliedAt: Instant,
    val scheduledFor: LocalDateTime? = null,
)
