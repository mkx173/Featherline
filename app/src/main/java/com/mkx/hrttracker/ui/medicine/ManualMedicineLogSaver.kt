package com.mkx.hrttracker.ui.medicine

import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal suspend fun saveManualMedicineLog(
    medicationLogRepository: MedicationLogRepository,
    medicationReminderScheduler: MedicationReminderScheduler,
    medicineUuid: UUID,
    resolvedApplicationType: MedicationApplicationType,
    doseInstruction: DoseInstruction,
    count: Int,
    appliedDate: LocalDate,
    appliedTime: LocalTime,
    appliedZoneId: ZoneId,
): MedicineSlotDraftSaveResult? {
    val appliedAt = LocalDateTime.of(
        appliedDate,
        appliedTime.withSecond(0).withNano(0),
    ).atZone(appliedZoneId).toInstant()
    val resolvedDose = if (resolvedApplicationType == MedicationApplicationType.PATCH_OFF) {
        DoseInstruction.Noop
    } else {
        doseInstruction
    }

    val saveResult = try {
        medicationLogRepository.saveEntry(
            uuid = null,
            medicineUuid = medicineUuid,
            applicationType = resolvedApplicationType,
            doseInstruction = resolvedDose,
            sourceGroupUuid = null,
            scheduleTimeUuid = null,
            appliedAt = appliedAt,
            scheduledFor = null,
            count = count.coerceAtLeast(1),
            appliedAtTimeZoneId = appliedZoneId.id,
        )
        null
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        MedicineSlotDraftSaveResult.FAILURE
    }
    if (saveResult == null) {
        withContext(NonCancellable) {
            runCatching { medicationReminderScheduler.rescheduleAll() }
        }
    }
    return saveResult
}
