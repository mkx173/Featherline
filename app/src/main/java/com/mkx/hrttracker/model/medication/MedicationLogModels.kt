package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class MedicationLogEntry(
    val uuid: UUID,
    val medicine: Medicine?,
    val category: MedicationCategory,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
    val equivalentE2Mg: Double?,
    val sourceGroupUuid: UUID?,
    val appliedAt: Instant,
    val appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
    val scheduledFor: LocalDateTime? = null,
    val count: Int = 1,
    val scheduleTimeUuid: UUID? = null,
    val doseAmountDelta: Double? = null,
) {
    init {
        require(count > 0) { "Medication log count must be at least 1." }
        require(medicine != null || applicationType == MedicationApplicationType.PATCH_OFF) {
            "Only a PATCH_OFF log may omit its medicine."
        }
        require(applicationType.isCompatibleWith(medicine?.preparation?.type)) {
            "applicationType=$applicationType is not compatible with preparation=${medicine?.preparation?.type}"
        }
        require(doseInstruction.isCompatibleWith(medicine?.preparation?.type)) {
            "doseInstruction=${doseInstruction.kind} is not compatible with preparation=${medicine?.preparation?.type}"
        }
    }

    val medicineUuid: UUID?
        get() = medicine?.uuid
}
