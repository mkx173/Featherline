package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicineStockRepository @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    @AppScope appScope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val projectionsFlow: StateFlow<List<MedicineStockProjection>?> =
        combine(
            medicineRepository.observeAllActive(),
            medicationGroupRepository.observeGroups(),
            medicationLogRepository.observeEntries(),
        ) { medicines, groups, logEntries ->
            projectAll(
                medicines = medicines,
                activeGroups = groups.orEmpty().filter { group -> group.archivedAt == null },
                logEntries = logEntries.orEmpty(),
                now = clock.instant(),
            )
        }
            .catch { emit(emptyList()) }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    fun observeProjections(): Flow<List<MedicineStockProjection>> = projectionsFlow.filterNotNull()

    /**
     * Synchronous read of the eagerly-cached projections. Returns `null` until
     * the combined upstream flows produce their first emission; lets
     * ViewModels seed `stateIn` `initialValue` so the first composition lands
     * on a populated stock subcard instead of a missing-then-popping one.
     */
    fun getCachedProjections(): List<MedicineStockProjection>? = projectionsFlow.value

    fun getCachedProjection(medicineUuid: UUID): MedicineStockProjection? {
        return projectionsFlow.value?.firstOrNull { it.medicine.uuid == medicineUuid }
    }

    internal fun projectAll(
        medicines: List<Medicine>,
        activeGroups: List<MedicationGroup>,
        logEntries: List<MedicationLogEntry> = emptyList(),
        now: Instant = clock.instant(),
    ): List<MedicineStockProjection> {
        val zoneId = scheduleZoneId()
        val stockWindowLogEntries = stockWindowLogEntries(logEntries, now, zoneId)
        return medicines.map { medicine ->
            project(medicine, activeGroups, stockWindowLogEntries, now, zoneId)
        }
    }

    suspend fun projectAllOnce(now: Instant = clock.instant()): List<MedicineStockProjection> {
        return projectAll(
            medicines = medicineRepository.getAllActive(),
            activeGroups = medicationGroupRepository.getActiveGroups(),
            logEntries = medicationLogRepository.getEntries(),
            now = now,
        )
    }

    private fun project(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
        logEntries: List<MedicationLogEntry>,
        now: Instant,
        zoneId: ZoneId,
    ): MedicineStockProjection {
        val total = computeTotalStock(medicine)
        val rate = computeDosesPerDayMagnitude(medicine, activeGroups)
        val runway = ScheduledRunwayCalculator.computeScheduledRunway(
            medicine = medicine,
            activeGroups = activeGroups,
            logEntries = logEntries,
            now = now,
            zoneId = zoneId,
        )
        val intervalDays = computeIntervalDays(medicine, activeGroups, now, zoneId)
        val maxPerAdministration = ScheduledRunwayCalculator.maxPerAdministration(
            medicine = medicine,
            activeGroups = activeGroups,
        )
        val imminentDoseCount = simulateNDoses(
            state = initialSimulatedStock(medicine),
            n = 2,
            perDose = maxPerAdministration,
        )
        val state = MedicineStockStateResolver.resolveState(
            trackingEnabled = medicine.stock.trackingEnabled,
            totalStockUnits = total,
            runway = runway,
            warnAtDaysRemaining = medicine.stock.warnAtDaysRemaining,
            imminentDoseCount = imminentDoseCount,
            maxPerAdministration = maxPerAdministration,
        )
        return MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = rate,
            totalStockUnits = total,
            runway = runway,
            intervalDays = intervalDays,
            maxPerAdministration = maxPerAdministration,
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
                    medicine.preparation,
                    slot.doseInstruction,
                ) ?: continue
                sum += slot.count.toDouble() * perAdministration * occurrencesPerDay
            }
        }
        return sum
    }

    private fun computeIntervalDays(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
        now: Instant,
        zoneId: ZoneId,
    ): Int? {
        val nowLocal = now.atZone(zoneId).toLocalDateTime()
        val horizonEnd = nowLocal.toLocalDate().plusDays(ScheduledRunwayCalculator.HORIZON_DAYS)
        val dates = activeGroups
            .asSequence()
            .filter { group -> group.medications.any { slot -> slot.medicine?.uuid == medicine.uuid } }
            .flatMap { group ->
                group.occurrencesBetweenInPlanWindow(
                    startDate = nowLocal.toLocalDate(),
                    endDate = horizonEnd,
                    zoneId = zoneId,
                ).asSequence()
            }
            .filter { occurrence -> !occurrence.scheduledFor.isBefore(nowLocal) }
            .map { occurrence -> occurrence.scheduledFor.toLocalDate() }
            .toSortedSet()
            .toList()
        if (dates.size < 2) return null
        return dates
            .zipWithNext { left, right -> ChronoUnit.DAYS.between(left, right).toInt() }
            .maxOrNull()
    }

    private fun stockWindowLogEntries(
        logEntries: List<MedicationLogEntry>,
        now: Instant,
        zoneId: ZoneId,
    ): List<MedicationLogEntry> {
        val today = now.atZone(zoneId).toLocalDate()
        val start = today.minusDays(1).atStartOfDay()
        val end = today
            .plusDays(ScheduledRunwayCalculator.HORIZON_DAYS)
            .atTime(23, 59, 59)
        return logEntries.filter { entry ->
            val scheduledFor = entry.scheduledFor ?: return@filter false
            entry.sourceGroupUuid != null &&
                !scheduledFor.isBefore(start) &&
                !scheduledFor.isAfter(end)
        }
    }

    private fun scheduleZoneId(): ZoneId = ZoneId.systemDefault()
}
