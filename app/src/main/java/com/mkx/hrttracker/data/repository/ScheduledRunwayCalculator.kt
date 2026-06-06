package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.isEntryFulfillingPlanSlot
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.toStorageValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

object ScheduledRunwayCalculator {
    internal const val HORIZON_DAYS = 365L

    fun computeScheduledRunway(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
        logEntries: List<MedicationLogEntry>,
        now: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        // Preview-only: a future scheduled slot the caller is about to fulfill with
        // a not-yet-persisted log. Excluded from demand so a pre-deducted preview
        // stock is not charged twice for the same occurrence.
        fulfilledSlot: FulfilledScheduledSlot? = null,
    ): RunwayProjection {
        if (!medicine.stock.trackingEnabled) return RunwayProjection.NoSchedule

        val nowLocal = now.atZone(zoneId).toLocalDateTime()
        val today = nowLocal.toLocalDate()
        val doses = enumerateUnfulfilledOccurrencesForMedicine(
            medicine = medicine,
            activeGroups = activeGroups,
            logEntries = logEntries,
            nowLocal = nowLocal,
            horizonEnd = today.plusDays(HORIZON_DAYS),
            zoneId = zoneId,
            fulfilledSlot = fulfilledSlot,
        )
        if (doses.isEmpty()) return RunwayProjection.NoSchedule

        var stock = initialSimulatedStock(medicine)
        var lastFulfillable: LocalDate? = null
        for (dose in doses) {
            val next = stock.applyDose(dose.perDose)
            if (next == null) {
                val last = lastFulfillable
                    ?: return RunwayProjection.Days(days = 0, lastFulfillable = today)
                return RunwayProjection.Days(
                    days = ChronoUnit.DAYS.between(today, last).toInt().coerceAtLeast(0),
                    lastFulfillable = last,
                )
            }
            stock = next
            lastFulfillable = dose.scheduledFor.toLocalDate()
        }

        return RunwayProjection.BeyondHorizon
    }

    internal fun enumerateUnfulfilledOccurrencesForMedicine(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
        logEntries: List<MedicationLogEntry>,
        nowLocal: LocalDateTime,
        horizonEnd: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        fulfilledSlot: FulfilledScheduledSlot? = null,
    ): List<MedicineDoseOccurrence> {
        if (!medicine.stock.trackingEnabled) return emptyList()

        val doses = mutableListOf<MedicineDoseOccurrence>()
        val logIndex = PlanSlotLogIndex(logEntries)
        for (group in activeGroups) {
            val required = requiredDosesBySignature(medicine = medicine, group = group)
            if (required.isEmpty()) continue

            val occurrences = group.occurrencesBetweenInPlanWindow(
                startDate = nowLocal.toLocalDate(),
                endDate = horizonEnd,
                zoneId = zoneId,
            )
            for (occurrence in occurrences) {
                if (occurrence.scheduledFor.isBefore(nowLocal)) continue
                // Drop the slot a not-yet-saved preview log will fulfill, so its
                // demand is not double-counted against already-deducted stock.
                if (fulfilledSlot != null &&
                    fulfilledSlot.groupUuid == group.uuid &&
                    fulfilledSlot.scheduleTimeUuid == occurrence.scheduleTimeUuid &&
                    fulfilledSlot.scheduledFor == occurrence.scheduledFor
                ) {
                    continue
                }

                val planSlot = MedicationGroupSlotKey(
                    scheduleTimeUuid = occurrence.scheduleTimeUuid,
                    scheduledFor = occurrence.scheduledFor,
                )
                val candidateLogs = logIndex.candidatesFor(group.uuid, planSlot)
                val loggedCounts = candidateLogs
                    .asSequence()
                    .filter { entry ->
                        isEntryFulfillingPlanSlot(
                            group = group,
                            slot = planSlot,
                            entry = entry,
                            zoneId = zoneId,
                        )
                    }
                    .groupBy(MedicationSignature::fromLogEntry)
                    .mapValues { (_, entries) -> entries.sumOf { entry -> entry.count } }

                required.forEach { (signature, requirement) ->
                    val remaining = requirement.requiredCount -
                        loggedCounts.getOrDefault(signature, 0)
                    if (remaining > 0) {
                        doses += MedicineDoseOccurrence(
                            scheduledFor = occurrence.scheduledFor,
                            groupCreatedAt = group.createdAt,
                            groupUuid = group.uuid,
                            scheduleTimeUuid = occurrence.scheduleTimeUuid,
                            signatureKey = signature.toStorageValue(),
                            perDose = requirement.perAdministration * remaining.toDouble(),
                        )
                    }
                }
            }
        }

        return doses.sortedWith(
            compareBy<MedicineDoseOccurrence> { dose -> dose.scheduledFor }
                .thenBy { dose -> dose.groupCreatedAt }
                .thenBy { dose -> dose.groupUuid.toString() }
                .thenBy { dose -> dose.scheduleTimeUuid.toString() }
                .thenBy { dose -> dose.signatureKey }
        )
    }

    internal fun upcomingOccurrenceDatesWithinHorizon(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
        logEntries: List<MedicationLogEntry>,
        now: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<LocalDate> {
        val nowLocal = now.atZone(zoneId).toLocalDateTime()
        return enumerateUnfulfilledOccurrencesForMedicine(
            medicine = medicine,
            activeGroups = activeGroups,
            logEntries = logEntries,
            nowLocal = nowLocal,
            horizonEnd = nowLocal.toLocalDate().plusDays(HORIZON_DAYS),
            zoneId = zoneId,
        )
            .map { dose -> dose.scheduledFor.toLocalDate() }
            .distinct()
            .sorted()
    }

    internal fun maxPerAdministration(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
    ): Double {
        return activeGroups
            .asSequence()
            .flatMap { group ->
                requiredDosesBySignature(medicine = medicine, group = group)
                    .asSequence()
                    .map { (_, requirement) ->
                        requirement.perAdministration * requirement.requiredCount.toDouble()
                    }
            }
            .maxOrNull()
            ?: 0.0
    }

    private fun requiredDosesBySignature(
        medicine: Medicine,
        group: MedicationGroup,
    ): Map<MedicationSignature, DoseRequirement> {
        return group.medications
            .asSequence()
            .filter { slot -> slot.medicineUuid == medicine.uuid }
            .mapNotNull { slot ->
                val perAdministration = resolvePerAdministrationMagnitude(
                    preparation = medicine.preparation,
                    doseInstruction = slot.doseInstruction,
                ) ?: return@mapNotNull null
                MedicationSignature.fromGroupMedication(slot) to DoseRequirement(
                    requiredCount = slot.count,
                    perAdministration = perAdministration,
                )
            }
            .groupBy({ (signature, _) -> signature }, { (_, requirement) -> requirement })
            .mapValues { (_, requirements) ->
                DoseRequirement(
                    requiredCount = requirements.sumOf { requirement -> requirement.requiredCount },
                    perAdministration = requirements.first().perAdministration,
                )
            }
    }

private data class DoseRequirement(
    val requiredCount: Int,
    val perAdministration: Double,
)
}

private data class LogPlanSlotKey(
    val groupUuid: UUID,
    val scheduledFor: LocalDateTime,
)

private data class LogScheduleTimeDateKey(
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID,
    val scheduledDate: LocalDate,
)

private class PlanSlotLogIndex(logEntries: List<MedicationLogEntry>) {
    private val byExactSlot: Map<LogPlanSlotKey, List<MedicationLogEntry>>
    private val byScheduleTimeDate: Map<LogScheduleTimeDateKey, List<MedicationLogEntry>>

    init {
        val exact = mutableMapOf<LogPlanSlotKey, MutableList<MedicationLogEntry>>()
        val scheduleTimeDate = mutableMapOf<LogScheduleTimeDateKey, MutableList<MedicationLogEntry>>()
        for (entry in logEntries) {
            val groupUuid = entry.sourceGroupUuid ?: continue
            val scheduledFor = entry.scheduledFor ?: continue
            exact.getOrPut(
                LogPlanSlotKey(
                    groupUuid = groupUuid,
                    scheduledFor = scheduledFor,
                )
            ) { mutableListOf() } += entry
            val scheduleTimeUuid = entry.scheduleTimeUuid
            if (scheduleTimeUuid != null) {
                scheduleTimeDate.getOrPut(
                    LogScheduleTimeDateKey(
                        groupUuid = groupUuid,
                        scheduleTimeUuid = scheduleTimeUuid,
                        scheduledDate = scheduledFor.toLocalDate(),
                    )
                ) { mutableListOf() } += entry
            }
        }
        byExactSlot = exact
        byScheduleTimeDate = scheduleTimeDate
    }

    fun candidatesFor(
        groupUuid: UUID,
        slot: MedicationGroupSlotKey,
    ): List<MedicationLogEntry> {
        val exact = byExactSlot[LogPlanSlotKey(groupUuid, slot.scheduledFor)].orEmpty()
        val scheduleTimeUuid = slot.scheduleTimeUuid ?: return exact
        val scheduleTimeDate = byScheduleTimeDate[
            LogScheduleTimeDateKey(
                groupUuid = groupUuid,
                scheduleTimeUuid = scheduleTimeUuid,
                scheduledDate = slot.scheduledFor.toLocalDate(),
            )
        ].orEmpty()
        if (scheduleTimeDate.isEmpty()) return exact
        if (exact.isEmpty()) return scheduleTimeDate
        return (scheduleTimeDate + exact).distinctBy { entry -> entry.uuid }
    }
}

internal data class MedicineDoseOccurrence(
    val scheduledFor: LocalDateTime,
    val groupCreatedAt: Instant,
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID,
    val signatureKey: String,
    val perDose: Double,
)

/**
 * Identifies a single scheduled occurrence a preview is about to fulfill with a
 * log that has not been persisted yet. Used to exclude that occurrence from
 * runway demand so a pre-deducted preview stock is not charged for it twice.
 */
data class FulfilledScheduledSlot(
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID,
    val scheduledFor: LocalDateTime,
)

internal fun initialSimulatedStock(medicine: Medicine): SimulatedStock {
    if (!medicine.stock.trackingEnabled) {
        return SimulatedStock(open = 0.0, sealed = 0.0, containerCapacity = 0.0, isContainer = false)
    }

    val units = medicine.stock.unitsRemaining ?: 0.0
    return when (medicine.preparation.type) {
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
        MedicinePreparationType.GEL_CONTAINER -> SimulatedStock(
            open = medicine.stock.openContainerAmount ?: 0.0,
            sealed = units,
            containerCapacity = medicine.preparation.containerCapacity(),
            isContainer = true,
        )

        else -> SimulatedStock(
            open = units,
            sealed = 0.0,
            containerCapacity = 0.0,
            isContainer = false,
        )
    }
}

internal fun resolvePerAdministrationMagnitude(
    preparation: MedicinePreparation,
    doseInstruction: DoseInstruction,
): Double? {
    return when (doseInstruction) {
        is DoseInstruction.TabletFraction -> {
            if (preparation is MedicinePreparation.Pill) {
                doseInstruction.numerator.toDouble() / doseInstruction.denominator.toDouble()
            } else {
                null
            }
        }

        DoseInstruction.WholeUnit -> when (preparation) {
            is MedicinePreparation.Capsule,
            is MedicinePreparation.InjectionSingleUseVial,
            is MedicinePreparation.GelSachet,
            is MedicinePreparation.Patch -> 1.0

            else -> null
        }

        is DoseInstruction.VolumeMl -> {
            if (preparation is MedicinePreparation.InjectionMultiUseVial) doseInstruction.valueMl else null
        }

        is DoseInstruction.WeightGrams -> {
            if (preparation is MedicinePreparation.GelContainer) doseInstruction.valueGrams else null
        }

        DoseInstruction.Noop -> null
    }
}

private fun MedicinePreparation.containerCapacity(): Double {
    return when (this) {
        is MedicinePreparation.InjectionMultiUseVial -> vialVolumeMl
        is MedicinePreparation.GelContainer -> containerWeightGrams
        else -> 0.0
    }
}
