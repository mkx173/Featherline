package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicineStockRepository @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val medicationGroupRepository: MedicationGroupRepository,
) {

    fun observeProjections(): Flow<List<MedicineStockProjection>> {
        return combine(
            medicineRepository.observeAllActive(),
            medicationGroupRepository.observeGroups(),
        ) { medicines, groups ->
            projectAll(
                medicines = medicines,
                activeGroups = groups.orEmpty().filter { group -> group.archivedAt == null },
            )
        }
    }

    internal fun projectAll(
        medicines: List<Medicine>,
        activeGroups: List<MedicationGroup>,
    ): List<MedicineStockProjection> {
        return medicines.map { medicine ->
            project(medicine, activeGroups)
        }
    }

    private fun project(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
    ): MedicineStockProjection {
        val total = computeTotalStock(medicine)
        val rate = computeDosesPerDayMagnitude(medicine, activeGroups)
        val runway = if (medicine.stock.trackingEnabled && rate > 0.0) total / rate else null
        val state = MedicineStockStateResolver.resolveState(
            trackingEnabled = medicine.stock.trackingEnabled,
            totalStockUnits = total,
            dosesPerDayMagnitude = rate,
            runwayDays = runway,
            warnAtDaysRemaining = medicine.stock.warnAtDaysRemaining,
        )
        return MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = rate,
            totalStockUnits = total,
            runwayDays = runway,
            state = state,
        )
    }

    private fun computeTotalStock(medicine: Medicine): Double {
        if (!medicine.stock.trackingEnabled) return 0.0

        val units = medicine.stock.unitsRemaining ?: 0.0
        return when (medicine.preparation.type) {
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
                val volumePerVial = (medicine.preparation as? MedicinePreparation.InjectionMultiUseVial)
                    ?.vialVolumeMl ?: 0.0
                (medicine.stock.openContainerAmount ?: 0.0) + units * volumePerVial
            }

            MedicinePreparationType.GEL_CONTAINER -> {
                val containerWeight = (medicine.preparation as? MedicinePreparation.GelContainer)
                    ?.containerWeightGrams ?: 0.0
                (medicine.stock.openContainerAmount ?: 0.0) + units * containerWeight
            }

            else -> units
        }
    }

    private fun computeDosesPerDayMagnitude(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
    ): Double {
        var sum = 0.0
        for (group in activeGroups) {
            val occurrencesPerDay = MedicineStockRateCalculator.occurrencesPerDay(
                scheduleType = group.schedule.type,
                interval = group.schedule.interval,
                weeklyDaysOfWeek = group.schedule.weeklyDaysOfWeek,
                scheduleTimesCount = group.schedule.times.size,
            )
            if (occurrencesPerDay == 0.0) continue

            for (slot in group.medications) {
                if (slot.medicine?.uuid != medicine.uuid) continue
                val perAdministration = resolvePerAdministrationMagnitude(
                    preparation = medicine.preparation,
                    doseInstruction = slot.doseInstruction,
                ) ?: continue
                sum += slot.count.toDouble() * perAdministration * occurrencesPerDay
            }
        }
        return sum
    }

    private fun resolvePerAdministrationMagnitude(
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
                if (preparation is MedicinePreparation.InjectionMultiUseVial) {
                    doseInstruction.valueMl
                } else {
                    null
                }
            }

            is DoseInstruction.WeightGrams -> {
                if (preparation is MedicinePreparation.GelContainer) {
                    doseInstruction.valueGrams
                } else {
                    null
                }
            }

            DoseInstruction.Noop -> null
        }
    }
}
