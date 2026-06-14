package com.mkx.hrttracker.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.ownsUnloggedOccurrence
import com.mkx.hrttracker.ui.plan.buildPlanBatchAddOccurrences
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDateTime
import java.time.ZoneId

class AutoLogWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint =
            EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val medicationGroupRepository = entryPoint.medicationGroupRepository()
        val medicationLogRepository = entryPoint.medicationLogRepository()
        val diagnosticsLogger = entryPoint.diagnosticsLogger()

        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()

        try {
            val activeGroups = medicationGroupRepository.getActiveGroups()
            val autoAddGroups = activeGroups.filter { it.autoAddFutureEntries }
            if (autoAddGroups.isEmpty()) {
                return Result.success()
            }

            val existingEntries = medicationLogRepository.getEntries()

            for (group in autoAddGroups) {
                // Check from since date or 1 day ago, up to now
                val startDate = maxOf(group.schedule.since, now.minusDays(1).toLocalDate())
                val endDate = now.toLocalDate()

                val occurrences = buildPlanBatchAddOccurrences(
                    schedule = group.schedule,
                    startDate = startDate,
                    endDate = endDate,
                )

                val fulfilledPlanSlots = existingEntries
                    .asSequence()
                    .filter { entry -> entry.sourceGroupUuid == group.uuid }
                    .mapNotNull(MedicationLogEntry::scheduledFor)
                    .toSet()

                val entriesToSave = mutableListOf<MedicationLogEntryInput>()

                for (occurrence in occurrences) {
                    if (occurrence.isAfter(now)) {
                        continue
                    }

                    // Check if already logged
                    if (occurrence in fulfilledPlanSlots) {
                        continue
                    }

                    val scheduleTime = group.schedule.timeSlots.firstOrNull { slot ->
                        slot.time == occurrence.toLocalTime()
                    } ?: continue

                    // Check if owns unlogged occurrence
                    if (!group.ownsUnloggedOccurrence(scheduleTime, occurrence)) {
                        continue
                    }

                    // Build entry
                    group.medications.forEach { medication ->
                        entriesToSave += MedicationLogEntryInput(
                            medicineUuid = medication.medicineUuid,
                            applicationType = medication.applicationType,
                            doseInstruction = medication.doseInstruction,
                            sourceGroupUuid = group.uuid,
                            scheduleTimeUuid = scheduleTime.uuid,
                            appliedAt = occurrence.atZone(zoneId).toInstant(),
                            scheduledFor = occurrence,
                            count = medication.count,
                            appliedAtTimeZoneId = zoneId.id,
                        )
                    }
                }

                if (entriesToSave.isNotEmpty()) {
                    diagnosticsLogger.info(
                        TAG,
                        "Auto-logging ${entriesToSave.size} entries for group ${group.name} (${group.uuid})"
                    )
                    medicationLogRepository.saveNewEntries(entriesToSave)
                }
            }
        } catch (e: Exception) {
            diagnosticsLogger.warning(TAG, "Error auto-logging doses", e)
            return Result.failure()
        }

        return Result.success()
    }

    private companion object {
        private const val TAG = "AutoLogWorker"
    }
}
