package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeSnapshotRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotStore: HomeSnapshotStore,
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore,
    @param:AppScope private val appScope: CoroutineScope,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val refreshMutex = Mutex()
    private val snapshotMutationMutex = Mutex()

    fun observeHomeSnapshot(): Flow<HomeSnapshotRecord?> {
        return combine(
            homeSnapshotStore.observeSnapshot(),
            homeSnapshotGenerationStore.observeGeneration(),
        ) { snapshot, generation ->
            when {
                snapshot == null -> {
                    diagnosticsLogger.info(
                        TAG,
                        "home_snapshot_observed_missing durableGeneration=$generation"
                    )
                    null
                }
                snapshot.generation < generation -> {
                    diagnosticsLogger.info(
                        TAG,
                        "home_snapshot_observed_rejected reason=generation " +
                            "snapshotGeneration=${snapshot.generation} durableGeneration=$generation"
                    )
                    null
                }
                else -> {
                    diagnosticsLogger.info(
                        TAG,
                        "home_snapshot_observed_loaded ${snapshot.diagnosticSummary()} " +
                            "durableGeneration=$generation"
                    )
                    snapshot
                }
            }
        }
            .distinctUntilChanged()
            .onEach { snapshot ->
                StartupTiming.mark(
                    if (snapshot == null) {
                        "home_snapshot_missing"
                    } else {
                        "home_snapshot_loaded"
                    }
                )
            }
    }

    // All medication group, medication log, and profile writes that affect Home data
    // must go through this gate so stale snapshots are rejected before the DB changes.
    suspend fun <T> runHomeDataMutation(block: suspend () -> T): T {
        return refreshMutex.withLock {
            diagnosticsLogger.info(TAG, "home_data_mutation_start")
            val generation = homeSnapshotGenerationStore.incrementGeneration()
            diagnosticsLogger.info(
                TAG,
                "home_data_mutation_generation_incremented generation=$generation"
            )
            try {
                val result = block()
                diagnosticsLogger.info(TAG, "home_data_mutation_committed generation=$generation")
                result
            } finally {
                // The generation has already been bumped, so observers will reject any
                // stale snapshot. Run cleanup + refresh on every path so an exception
                // inside block() doesn't leave the snapshot stale-flagged forever.
                clearSnapshotBestEffort()
                refreshHomeSnapshotAsync(force = true)
                diagnosticsLogger.info(
                    TAG,
                    "home_data_mutation_snapshot_refresh_requested generation=$generation"
                )
            }
        }
    }

    fun isSnapshotUsable(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        return snapshotUsabilityFailure(
            snapshot = snapshot,
            now = now,
            zoneId = zoneId,
        ) == null
    }

    suspend fun readUsableHomeSnapshot(
        now: LocalDateTime = LocalDateTime.now(),
    ): HomeSnapshotRecord? {
        val snapshot = homeSnapshotStore.readSnapshot()
            ?: return null.also {
                diagnosticsLogger.info(TAG, "home_snapshot_read_missing now=$now")
            }
        val generation = homeSnapshotGenerationStore.readGeneration()
        if (snapshot.generation < generation) {
            diagnosticsLogger.info(
                TAG,
                "home_snapshot_read_rejected reason=generation " +
                    "snapshotGeneration=${snapshot.generation} durableGeneration=$generation now=$now"
            )
            return null
        }
        val zoneId = ZoneId.systemDefault()
        val usabilityFailure = snapshotUsabilityFailure(
            snapshot = snapshot,
            now = now,
            zoneId = zoneId,
        )
        if (usabilityFailure != null) {
            diagnosticsLogger.info(
                TAG,
                "home_snapshot_read_rejected reason=$usabilityFailure " +
                    "${snapshot.diagnosticSummary()} now=$now"
            )
            return null
        }
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_read_usable ${snapshot.diagnosticSummary()} " +
                "durableGeneration=$generation now=$now"
        )
        return snapshot
    }

    fun scheduleEntriesForHome(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<MedicationLogEntry> {
        val window = HomeSnapshotWindow.forNow(now = now, zoneId = zoneId)
        return snapshot.scheduleEntries.filter { entry ->
            val scheduledFor = entry.scheduledFor
            if (scheduledFor != null) {
                scheduledFor >= window.homeScheduledStart &&
                    scheduledFor <= window.homeScheduledEnd
            } else {
                val appliedAtEpochMillis = entry.appliedAt.toEpochMilli()
                appliedAtEpochMillis >= window.homeManualStartEpochMillis &&
                    appliedAtEpochMillis < window.homeManualEndEpochMillis
            }
        }
    }

    fun decodeProjection(projectionRecord: HomePkProjectionRecord?): PkProjectionResult? {
        projectionRecord ?: return null
        return runCatching {
            val unit = runCatching {
                com.mkx.hrttracker.model.pk.PkConcentrationUnit.valueOf(projectionRecord.concentrationUnit)
            }.getOrNull() ?: return null

            PkProjectionResult(
                generatedAt = java.time.Instant.ofEpochMilli(projectionRecord.generatedAtEpochMillis),
                windowStart = java.time.Instant.ofEpochMilli(projectionRecord.windowStartEpochMillis),
                windowEnd = java.time.Instant.ofEpochMilli(projectionRecord.windowEndEpochMillis),
                concentrationUnit = unit,
                timeH = projectionRecord.timeH,
                concentrations = projectionRecord.concentrations,
                doseMarkers = projectionRecord.doseMarkers.map { marker ->
                    com.mkx.hrttracker.model.pk.PkDoseMarker(
                        timeH = marker.timeH,
                        concentration = marker.concentration,
                    )
                },
            )
        }.getOrNull()
    }

    private fun snapshotUsabilityFailure(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime,
        zoneId: ZoneId,
    ): String? {
        if (snapshot.schemaVersion != HOME_SNAPSHOT_SCHEMA_VERSION) {
            return "schema_version expected=$HOME_SNAPSHOT_SCHEMA_VERSION actual=${snapshot.schemaVersion}"
        }
        if (snapshot.zoneId != zoneId.id) {
            return "zone_id expected=${zoneId.id} actual=${snapshot.zoneId}"
        }
        val anchorDate = LocalDate.ofEpochDay(snapshot.anchorDateEpochDay)
        val today = now.toLocalDate()
        val validUntil = anchorDate.plusDays(HOME_SNAPSHOT_VALIDITY_DAYS)
        if (today.isBefore(anchorDate) || today.isAfter(validUntil)) {
            return "date_window anchorDate=$anchorDate validUntil=$validUntil today=$today"
        }
        val pkProjection = snapshot.pkProjection ?: return "missing_pk_projection"
        val chartWindow = HomePkChartWindow.forNow(now = now, zoneId = zoneId)
        if (
            pkProjection.windowStartEpochMillis > chartWindow.windowStartEpochMillis ||
            pkProjection.windowEndEpochMillis < chartWindow.windowEndEpochMillis
        ) {
            return "pk_projection_window " +
                "projectionStart=${pkProjection.windowStartEpochMillis} " +
                "projectionEnd=${pkProjection.windowEndEpochMillis} " +
                "requiredStart=${chartWindow.windowStartEpochMillis} " +
                "requiredEnd=${chartWindow.windowEndEpochMillis}"
        }
        return null
    }

    fun refreshHomeSnapshotAsync(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
    ) {
        diagnosticsLogger.info(TAG, "home_snapshot_refresh_async_enqueued force=$force now=$now")
        appScope.launch {
            try {
                refreshHomeSnapshotIfNeeded(now = now, force = force)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                diagnosticsLogger.warning(TAG, "home_snapshot_refresh_failed force=$force now=$now", throwable)
            }
        }
    }

    suspend fun invalidateHomeSnapshot() {
        diagnosticsLogger.info(TAG, "home_snapshot_invalidate_start")
        val generation = homeSnapshotGenerationStore.incrementGeneration()
        diagnosticsLogger.info(TAG, "home_snapshot_invalidate_generation_incremented generation=$generation")
        snapshotMutationMutex.withLock {
            homeSnapshotStore.clearSnapshot()
            diagnosticsLogger.info(TAG, "home_snapshot_invalidated generation=$generation")
        }
    }

    suspend fun refreshHomeSnapshotIfNeeded(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
    ) {
        diagnosticsLogger.info(TAG, "home_snapshot_refresh_start force=$force now=$now")
        val zoneId = ZoneId.systemDefault()
        val cacheWindow = HomePkProjectionWindow.forNow(now = now, zoneId = zoneId)
        val snapshotWindow = HomeSnapshotWindow.forNow(now = now, zoneId = zoneId)

        // refreshMutex is held only across the skip-check so concurrent refresh
        // requests briefly coalesce here. The simulation and write phases run
        // outside the lock; correctness relies on the generation recheck inside
        // snapshotMutationMutex below — any racing mutation bumps the durable
        // generation and our writeSnapshot call is dropped. Trade-off: under
        // unusual contention, two refresh requests may both run their
        // simulations in parallel and the loser's work is wasted. We accept
        // that to keep mutation latency low (writes don't stall behind a PK
        // simulation + JSON encode + encrypted DataStore write).
        val refreshGeneration = refreshMutex.withLock {
            val gen = homeSnapshotGenerationStore.readGeneration()
            val existingSnapshot = homeSnapshotStore.readSnapshot()
            diagnosticsLogger.info(
                TAG,
                "home_snapshot_refresh_verify_existing force=$force " +
                    "generation=$gen " +
                    "existing=${existingSnapshot?.diagnosticSummary() ?: "none"}"
            )
            if (
                !force &&
                existingSnapshot != null &&
                existingSnapshot.generation >= gen &&
                isSnapshotUsable(existingSnapshot, now, zoneId)
            ) {
                diagnosticsLogger.info(
                    TAG,
                    "home_snapshot_refresh_skipped reason=existing_usable " +
                        "${existingSnapshot.diagnosticSummary()} now=$now"
                )
                return
            }
            gen
        }

        val inputs = withContext(Dispatchers.IO) {
            val database = databaseHolder.get()
            val homeDao = database.homeDao()
            val activeGroups = homeDao.getActiveGroups()
                .map { group -> group.toMedicationGroupModel() }
            val scheduleEntries = homeDao.getScheduleEntries(
                scheduledStartIso = snapshotWindow.bufferedScheduledStart.toString(),
                scheduledEndIso = snapshotWindow.bufferedScheduledEnd.toString(),
                manualStartEpochMillis = snapshotWindow.bufferedManualStartEpochMillis,
                manualEndEpochMillis = snapshotWindow.bufferedManualEndEpochMillis,
            ).map { entry -> entry.toMedicationLogEntryModel() }
            val antiandrogenHistoryEntries = homeDao.getLatestAntiandrogenEntriesOnOrBefore(
                onOrBeforeEpochMillis = snapshotWindow.onOrBeforeEpochMillis,
            ).map { entry -> entry.toMedicationLogEntryModel() }
            val pkEntries = homeDao.getEstradiolPkEntries(
                startEpochMillis = cacheWindow.inputStartEpochMillis,
                endEpochMillis = cacheWindow.generatedAtEpochMillis,
            ).map { entry -> entry.toMedicationLogEntryModel() }
            val latestEstradiolEntry = homeDao.getLatestEstradiolEntryOnOrBefore(
                onOrBeforeEpochMillis = cacheWindow.generatedAtEpochMillis,
            )?.toMedicationLogEntryModel()
            HomeSnapshotBuildInputs(
                activeGroups = activeGroups,
                scheduleEntries = scheduleEntries,
                antiandrogenHistoryEntries = antiandrogenHistoryEntries,
                pkEntries = pkEntries,
                latestEstradiolEntry = latestEstradiolEntry,
                profile = database.userProfileDao()
                    .getProfile()
                    ?.toUserProfileModel()
                    ?: UserProfile(),
            )
        }
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_refresh_inputs_loaded generation=$refreshGeneration " +
                "activeGroups=${inputs.activeGroups.size} " +
                "scheduleEntries=${inputs.scheduleEntries.size} " +
                "antiandrogenEntries=${inputs.antiandrogenHistoryEntries.size} " +
                "pkEntries=${inputs.pkEntries.size} " +
                "hasLatestEstradiol=${inputs.latestEstradiolEntry != null}"
        )

        val projection = withContext(defaultDispatcher) {
            PkMedicationSimulation.simulateMainEstradiolProjection(
                entries = inputs.pkEntries,
                bodyWeightKg = inputs.profile.weightKg,
                generatedAt = now,
                zoneId = zoneId,
                futureDays = HOME_PK_PROJECTION_FUTURE_DAYS,
            )
        }
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_refresh_projection_built generation=$refreshGeneration " +
                "windowStart=${projection.windowStart} windowEnd=${projection.windowEnd}"
        )
        val pkProjectionRecord = HomePkProjectionRecord(
            generatedAtEpochMillis = projection.generatedAt.toEpochMilli(),
            windowStartEpochMillis = projection.windowStart.toEpochMilli(),
            windowEndEpochMillis = projection.windowEnd.toEpochMilli(),
            concentrationUnit = projection.concentrationUnit.name,
            timeH = projection.timeH,
            concentrations = projection.concentrations,
            doseMarkers = projection.doseMarkers.map { marker ->
                HomePkProjectionDoseMarkerRecord(
                    timeH = marker.timeH,
                    concentration = marker.concentration,
                )
            },
            latestEstradiolEntry = inputs.latestEstradiolEntry,
        )
        val snapshotRecord = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = refreshGeneration,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            pkProjection = pkProjectionRecord,
            activeGroups = inputs.activeGroups,
            scheduleEntries = inputs.scheduleEntries,
            antiandrogenHistoryEntries = inputs.antiandrogenHistoryEntries,
            userProfile = inputs.profile,
        )

        withContext(Dispatchers.IO) {
            // Avoid restoring stale snapshot data from a refresh that raced with a write.
            snapshotMutationMutex.withLock {
                if (homeSnapshotGenerationStore.readGeneration() == refreshGeneration) {
                    diagnosticsLogger.info(
                        TAG,
                        "home_snapshot_write_start ${snapshotRecord.diagnosticSummary()}"
                    )
                    homeSnapshotStore.writeSnapshot(snapshotRecord)
                    StartupTiming.mark("home_snapshot_rebuilt")
                    diagnosticsLogger.info(
                        TAG,
                        "home_snapshot_refreshed ${snapshotRecord.diagnosticSummary()}"
                    )
                } else {
                    diagnosticsLogger.info(
                        TAG,
                        "home_snapshot_write_skipped reason=generation_changed " +
                            "refreshGeneration=$refreshGeneration " +
                            "currentGeneration=${homeSnapshotGenerationStore.readGeneration()}"
                    )
                }
            }
        }
    }

    private data class HomeSnapshotBuildInputs(
        val activeGroups: List<MedicationGroup>,
        val scheduleEntries: List<MedicationLogEntry>,
        val antiandrogenHistoryEntries: List<MedicationLogEntry>,
        val pkEntries: List<MedicationLogEntry>,
        val latestEstradiolEntry: MedicationLogEntry?,
        val profile: UserProfile,
    )

    private data class HomePkProjectionWindow(
        val generatedAtEpochMillis: Long,
        val windowStartEpochMillis: Long,
        val windowEndEpochMillis: Long,
        val inputStartEpochMillis: Long,
    ) {
        companion object {
            fun forNow(
                now: LocalDateTime,
                zoneId: ZoneId,
            ): HomePkProjectionWindow {
                val generatedAt = now.atZone(zoneId).toInstant()
                val windowStart = now
                    .toLocalDate()
                    .atStartOfDay()
                    .minusDays(PkMedicationSimulation.mainChartPastDays)
                    .atZone(zoneId)
                    .toInstant()
                val windowEnd = now
                    .toLocalDate()
                    .plusDays(HOME_PK_PROJECTION_FUTURE_DAYS)
                    .atStartOfDay()
                    .atZone(zoneId)
                    .toInstant()
                return HomePkProjectionWindow(
                    generatedAtEpochMillis = generatedAt.toEpochMilli(),
                    windowStartEpochMillis = windowStart.toEpochMilli(),
                    windowEndEpochMillis = windowEnd.toEpochMilli(),
                    inputStartEpochMillis = windowStart
                        .minus(Duration.ofDays(HOME_PK_PROJECTION_LOOKBACK_DAYS))
                        .toEpochMilli(),
                )
            }
        }
    }

    private data class HomePkChartWindow(
        val windowStartEpochMillis: Long,
        val windowEndEpochMillis: Long,
    ) {
        companion object {
            fun forNow(
                now: LocalDateTime,
                zoneId: ZoneId,
            ): HomePkChartWindow {
                val windowStart = now
                    .toLocalDate()
                    .atStartOfDay()
                    .minusDays(PkMedicationSimulation.mainChartPastDays)
                    .atZone(zoneId)
                    .toInstant()
                val windowEnd = windowStart.plus(
                    Duration.ofHours(PkMedicationSimulation.mainChartWindowHours)
                )
                return HomePkChartWindow(
                    windowStartEpochMillis = windowStart.toEpochMilli(),
                    windowEndEpochMillis = windowEnd.toEpochMilli(),
                )
            }
        }
    }

    private data class HomeSnapshotWindow(
        val homeScheduledStart: LocalDateTime,
        val homeScheduledEnd: LocalDateTime,
        val homeManualStartEpochMillis: Long,
        val homeManualEndEpochMillis: Long,
        val bufferedScheduledStart: LocalDateTime,
        val bufferedScheduledEnd: LocalDateTime,
        val bufferedManualStartEpochMillis: Long,
        val bufferedManualEndEpochMillis: Long,
        val onOrBeforeEpochMillis: Long,
    ) {
        companion object {
            fun forNow(
                now: LocalDateTime,
                zoneId: ZoneId,
            ): HomeSnapshotWindow {
                val today = now.toLocalDate()
                return HomeSnapshotWindow(
                    homeScheduledStart = today.minusDays(1).atStartOfDay(),
                    homeScheduledEnd = today.plusDays(HOME_SCHEDULE_LOOKAHEAD_DAYS)
                        .atTime(23, 59, 59),
                    homeManualStartEpochMillis = today.minusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    homeManualEndEpochMillis = today.plusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    bufferedScheduledStart = today.minusDays(HOME_SNAPSHOT_PAST_BUFFER_DAYS)
                        .atStartOfDay(),
                    bufferedScheduledEnd = today.plusDays(HOME_SCHEDULE_LOOKAHEAD_DAYS + HOME_SNAPSHOT_VALIDITY_DAYS)
                        .atTime(23, 59, 59),
                    bufferedManualStartEpochMillis = today.minusDays(HOME_SNAPSHOT_PAST_BUFFER_DAYS)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    bufferedManualEndEpochMillis = today.plusDays(HOME_SNAPSHOT_VALIDITY_DAYS + 1)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    onOrBeforeEpochMillis = now
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                )
            }
        }
    }

    private suspend fun clearSnapshotBestEffort() {
        snapshotMutationMutex.withLock {
            clearSnapshotBestEffortLocked()
        }
    }

    private suspend fun clearSnapshotBestEffortLocked() {
        runCatching {
            homeSnapshotStore.clearSnapshot()
            diagnosticsLogger.info(TAG, "home_snapshot_cleared_best_effort")
        }.onFailure { throwable ->
            diagnosticsLogger.warning(TAG, "home_snapshot_clear_failed", throwable)
        }
    }
}

private fun HomeSnapshotRecord.diagnosticSummary(): String {
    return "schema=$schemaVersion " +
        "generation=$generation " +
        "anchorDate=${LocalDate.ofEpochDay(anchorDateEpochDay)} " +
        "zone=$zoneId " +
        "groups=${activeGroups.size} " +
        "scheduleEntries=${scheduleEntries.size} " +
        "antiandrogenEntries=${antiandrogenHistoryEntries.size} " +
        "hasPkProjection=${pkProjection != null}"
}

internal const val HOME_SNAPSHOT_SCHEMA_VERSION = 4
private const val TAG = "HomeSnapshotRepository"
private const val HOME_PK_PROJECTION_LOOKBACK_DAYS = 180L
private const val HOME_PK_PROJECTION_FUTURE_DAYS = 14L
private const val HOME_SCHEDULE_LOOKAHEAD_DAYS = 90L
private const val HOME_SNAPSHOT_VALIDITY_DAYS = 10L
private const val HOME_SNAPSHOT_PAST_BUFFER_DAYS = 1L
