package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
    private val homeSnapshotRepository: HomeSnapshotRepository,
    @AppScope appScope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    // Tracks both the projection list and whether it was derived from live
    // upstream data or from the home snapshot. The medicine manager only
    // wants live data (so the manager rows don't visibly shift from
    // snapshot-derived to live values). The AddEntry sheet wants whatever's
    // cached, including the snapshot, so its subcard renders on frame 1
    // before Room finishes opening.
    private data class ProjectionsCache(
        val projections: List<MedicineStockProjection>,
        val medicines: List<Medicine>,
        val activeGroups: List<MedicationGroup>,
        val logEntries: List<MedicationLogEntry>,
        val now: Instant,
        val isLive: Boolean,
    )

    private val projectionsCacheFlow: StateFlow<ProjectionsCache?> =
        combine(
            medicineRepository.observeAllActiveOrNull(),
            medicationGroupRepository.observeGroups(),
            medicationLogRepository.observeEntries(),
            homeSnapshotRepository.observeHomeSnapshot(),
        ) { medicines, groups, logEntries, snapshot ->
            val now = clock.instant()
            when {
                medicines != null && groups != null && logEntries != null -> {
                    val activeGroups = groups.filter { group -> group.archivedAt == null }
                    ProjectionsCache(
                        projections = projectAll(
                            medicines = medicines,
                            activeGroups = activeGroups,
                            logEntries = logEntries,
                            now = now,
                        ),
                        medicines = medicines,
                        activeGroups = activeGroups,
                        logEntries = logEntries,
                        now = now,
                        isLive = true,
                    )
                }
                snapshot != null -> {
                    ProjectionsCache(
                        projections = projectAll(
                            medicines = snapshot.stockMedicines,
                            activeGroups = snapshot.activeGroups,
                            logEntries = snapshot.stockFulfillmentEntries,
                            now = now,
                        ),
                        medicines = snapshot.stockMedicines,
                        activeGroups = snapshot.activeGroups,
                        logEntries = snapshot.stockFulfillmentEntries,
                        now = now,
                        isLive = false,
                    )
                }
                else -> null
            }
        }
            .catch {
                emit(
                    ProjectionsCache(
                        projections = emptyList(),
                        medicines = emptyList(),
                        activeGroups = emptyList(),
                        logEntries = emptyList(),
                        now = clock.instant(),
                        isLive = true,
                    )
                )
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    // Live-only: emits when all three live inputs are non-null. The medicine
    // manager subscribes to this so its combine fires once with a complete
    // projection — never with a snapshot-derived list that would re-render
    // when live data lands.
    fun observeProjections(): Flow<List<MedicineStockProjection>> =
        projectionsCacheFlow
            .filterNotNull()
            .filter { it.isLive }
            .map { it.projections }

    /**
     * Synchronous read of the eagerly-cached projections. Returns `null` until
     * the combined upstream flows produce their first emission; lets
     * ViewModels seed `stateIn` `initialValue` so the first composition lands
     * on a populated stock subcard instead of a missing-then-popping one.
     */
    fun getCachedProjections(): List<MedicineStockProjection>? =
        projectionsCacheFlow.value?.projections

    fun getCachedProjection(medicineUuid: UUID): MedicineStockProjection? {
        return projectionsCacheFlow.value?.projections
            ?.firstOrNull { it.medicine.uuid == medicineUuid }
    }

    fun previewRunway(
        medicineUuid: UUID,
        hypotheticalStock: MedicineStock,
    ): RunwayProjection? {
        val cache = projectionsCacheFlow.value ?: return null
        val medicine = cache.medicines.firstOrNull { it.uuid == medicineUuid } ?: return null
        val zoneId = scheduleZoneId()
        return ScheduledRunwayCalculator.computeScheduledRunway(
            medicine = medicine.copy(stock = hypotheticalStock),
            activeGroups = cache.activeGroups,
            logEntries = stockWindowLogEntries(cache.logEntries, cache.now, zoneId),
            now = cache.now,
            zoneId = zoneId,
        )
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
