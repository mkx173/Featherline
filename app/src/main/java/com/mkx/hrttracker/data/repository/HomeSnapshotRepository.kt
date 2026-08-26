package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.buildEstradiolPkDoseEvent
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkPersonalParams
import java.time.Instant
import java.util.UUID
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.pk.buildEstradiolPkSimulationEntries
import com.mkx.hrttracker.model.pk.projectionFutureDays
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private val settingsRepository: SettingsRepository,
    @param:AppScope private val appScope: CoroutineScope,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val refreshMutex = Mutex()
    private val snapshotMutationMutex = Mutex()

    init {
        // Observe option changes (not the initial value) and force a rebuild
        // when the user toggles 7-day/30-day. The first emission is the
        // persisted value at process start, not a user change, so it is
        // ignored. Refresh and validation paths resolve the option directly
        // from settingsRepository.homeE2ChartWindowOptionFlow at use-time;
        // they do not depend on this observer having run first.
        appScope.launch {
            var previous: HomeE2ChartWindowOption? = null
            settingsRepository.homeE2ChartWindowOptionFlow.collect { option ->
                val old = previous
                previous = option
                if (old != null && old != option) {
                    diagnosticsLogger.info(
                        TAG,
                        "home_snapshot_option_changed previous=$old current=$option"
                    )
                    // Best-effort: appScope has no exception handler, so a DataStore failure
                    // during invalidation must not tear down this long-lived collector.
                    runCatching {
                        invalidateHomeSnapshot()
                        refreshHomeSnapshotAsync(force = true)
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) {
                            throw throwable
                        }
                        diagnosticsLogger.warning(
                            TAG,
                            "home_snapshot_option_change_refresh_failed previous=$old current=$option",
                            throwable
                        )
                    }
                }
            }
        }
    }

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
    //
    // The work inside the lock runs in NonCancellable: once we bump the generation
    // and clear the snapshot, the new snapshot MUST be rewritten — otherwise a
    // caller whose scope dies mid-mutation (e.g. an OnboardingViewModel's
    // viewModelScope when the user finishes onboarding right after saving weight)
    // leaves the snapshot store empty and the next launch reads no profile until
    // Room finishes opening. Lock acquisition stays cancellable so callers blocked
    // on a long-running peer can still be cancelled cleanly.
    suspend fun <T> runHomeDataMutation(block: suspend () -> T): T {
        var generation = 0L
        var mutationResult: Result<T>? = null
        refreshMutex.withLock {
            withContext(NonCancellable) {
                diagnosticsLogger.info(TAG, "home_data_mutation_start")
                generation = homeSnapshotGenerationStore.incrementGeneration()
                diagnosticsLogger.info(
                    TAG,
                    "home_data_mutation_generation_incremented generation=$generation"
                )
                val result = runCatching { block() }
                mutationResult = result
                result.onSuccess {
                    diagnosticsLogger.info(
                        TAG,
                        "home_data_mutation_committed generation=$generation"
                    )
                }
                // The generation has already been bumped, so observers will reject any
                // stale snapshot. Clear while still serialized with other mutations.
                clearSnapshotBestEffort()
                refreshHomeSnapshotBestEffortLocked(force = true)
            }
        }
        diagnosticsLogger.info(
            TAG,
            "home_data_mutation_snapshot_refresh_completed generation=$generation"
        )
        return checkNotNull(mutationResult).getOrThrow()
    }

    /** Captures the durable generation while serialized with Home data mutations. */
    internal suspend fun captureCurrentHomeDataGeneration(): Long {
        return refreshMutex.withLock {
            homeSnapshotGenerationStore.readGeneration()
        }
    }

    /** Read-only observation of the same durable generation used by Home mutations. */
    internal fun observeCurrentHomeDataGeneration(): Flow<Long> {
        return homeSnapshotGenerationStore.observeGeneration()
    }

    fun isSnapshotUsable(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
        option: HomeE2ChartWindowOption,
    ): Boolean {
        return snapshotUsabilityFailure(
            snapshot = snapshot,
            now = now,
            zoneId = zoneId,
            option = option,
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
        // Self-resolving so startup and reminder callers (which don't have the
        // option in hand) still validate against the user's persisted choice
        // rather than the eager SEVEN_DAYS default.
        val option = settingsRepository.homeE2ChartWindowOptionFlow.first()
        val usabilityFailure = snapshotUsabilityFailure(
            snapshot = snapshot,
            now = now,
            zoneId = zoneId,
            option = option,
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

    fun decodeProjection(
        projectionRecord: HomePkProjectionRecord?,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PkProjectionResult? {
        projectionRecord ?: return null
        if (isPkProjectionExpired(projectionRecord, now, zoneId)) {
            diagnosticsLogger.info(
                TAG,
                "home_snapshot_pk_projection_expired now=$now " +
                        "expiresAt=${projectionRecord.pkProjectionExpiresAtEpochMillis}"
            )
            return null
        }
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
                        isPlanned = marker.isPlanned,
                    )
                },
            )
        }.getOrNull()
    }

    fun isPkProjectionExpired(
        projectionRecord: HomePkProjectionRecord,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()
        return nowEpochMillis >= projectionRecord.pkProjectionExpiresAtEpochMillis
    }

    private fun snapshotUsabilityFailure(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime,
        zoneId: ZoneId,
        option: HomeE2ChartWindowOption,
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
        val chartWindow = HomePkChartWindow.forNow(now = now, zoneId = zoneId, option = option)
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
        val expectedChartWindowHours = option.chartWindowHours.toInt()
        if (pkProjection.chartWindowHours != expectedChartWindowHours) {
            return "pk_projection_chart_window_hours " +
                    "expected=$expectedChartWindowHours " +
                    "actual=${pkProjection.chartWindowHours}"
        }
        val expectedDensePolicy = option.densePolicy.toRecord()
        if (pkProjection.densePolicy != expectedDensePolicy) {
            return "pk_projection_dense_policy " +
                    "expected=$expectedDensePolicy " +
                    "actual=${pkProjection.densePolicy}"
        }
        if (pkProjection.includesPostDoseOffsets != option.includesPostDoseOffsets) {
            return "pk_projection_post_dose_offsets " +
                    "expected=${option.includesPostDoseOffsets} " +
                    "actual=${pkProjection.includesPostDoseOffsets}"
        }
        return null
    }

    fun refreshHomeSnapshotAsync(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        diagnosticsLogger.info(TAG, "home_snapshot_refresh_async_enqueued force=$force now=$now")
        appScope.launch {
            try {
                refreshHomeSnapshotIfNeeded(now = now, force = force, zoneId = zoneId)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                diagnosticsLogger.warning(
                    TAG,
                    "home_snapshot_refresh_failed force=$force now=$now",
                    throwable
                )
            }
        }
    }

    suspend fun invalidateHomeSnapshot() {
        diagnosticsLogger.info(TAG, "home_snapshot_invalidate_start")
        val generation = homeSnapshotGenerationStore.incrementGeneration()
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_invalidate_generation_incremented generation=$generation"
        )
        snapshotMutationMutex.withLock {
            homeSnapshotStore.clearSnapshot()
            diagnosticsLogger.info(TAG, "home_snapshot_invalidated generation=$generation")
        }
    }

    suspend fun refreshHomeSnapshotIfNeeded(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        diagnosticsLogger.info(TAG, "home_snapshot_refresh_start force=$force now=$now")
        // Suspends on first call until the observer has captured the raw
        // DataStore value; subsequent calls return immediately.
        val option = settingsRepository.homeE2ChartWindowOptionFlow.first()
        val cacheWindow = HomePkProjectionWindow.forNow(now = now, zoneId = zoneId, option = option)
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
            refreshGenerationAfterSkipCheckLocked(
                now = now,
                zoneId = zoneId,
                option = option,
                force = force,
            )
        } ?: return

        buildAndWriteHomeSnapshot(
            refreshGeneration = refreshGeneration,
            now = now,
            zoneId = zoneId,
            option = option,
            cacheWindow = cacheWindow,
            snapshotWindow = snapshotWindow,
        )
    }

    private suspend fun refreshHomeSnapshotIfNeededLocked(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        diagnosticsLogger.info(TAG, "home_snapshot_refresh_start force=$force now=$now")
        val option = settingsRepository.homeE2ChartWindowOptionFlow.first()
        val cacheWindow = HomePkProjectionWindow.forNow(now = now, zoneId = zoneId, option = option)
        val snapshotWindow = HomeSnapshotWindow.forNow(now = now, zoneId = zoneId)
        val refreshGeneration = refreshGenerationAfterSkipCheckLocked(
            now = now,
            zoneId = zoneId,
            option = option,
            force = force,
        ) ?: return

        buildAndWriteHomeSnapshot(
            refreshGeneration = refreshGeneration,
            now = now,
            zoneId = zoneId,
            option = option,
            cacheWindow = cacheWindow,
            snapshotWindow = snapshotWindow,
        )
    }

    private suspend fun refreshGenerationAfterSkipCheckLocked(
        now: LocalDateTime,
        zoneId: ZoneId,
        option: HomeE2ChartWindowOption,
        force: Boolean,
    ): Long? {
        val gen = homeSnapshotGenerationStore.readGeneration()
        val existingSnapshot = homeSnapshotStore.readSnapshot()
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_refresh_verify_existing force=$force " +
                    "generation=$gen " +
                    "existing=${existingSnapshot?.diagnosticSummary() ?: "none"}"
        )
        val pkExpired = existingSnapshot?.pkProjection?.let { record ->
            isPkProjectionExpired(record, now, zoneId)
        } == true
        if (
            !force &&
            existingSnapshot != null &&
            existingSnapshot.generation >= gen &&
            isSnapshotUsable(existingSnapshot, now, zoneId, option) &&
            !pkExpired
        ) {
            diagnosticsLogger.info(
                TAG,
                "home_snapshot_refresh_skipped reason=existing_usable " +
                        "${existingSnapshot.diagnosticSummary()} now=$now"
            )
            return null
        }
        if (pkExpired) {
            diagnosticsLogger.info(
                TAG,
                "home_snapshot_refresh_continuing reason=pk_projection_expired " +
                        "${existingSnapshot.diagnosticSummary()} now=$now"
            )
        }
        return gen
    }

    private suspend fun buildAndWriteHomeSnapshot(
        refreshGeneration: Long,
        now: LocalDateTime,
        zoneId: ZoneId,
        option: HomeE2ChartWindowOption,
        cacheWindow: HomePkProjectionWindow,
        snapshotWindow: HomeSnapshotWindow,
    ) {
        val inputs = withContext(Dispatchers.IO) {
            // awaitOpen, not get(): this runs inside WidgetDateReceiver's bounded goAsync
            // window, and the blocking get() is uncancellable — a stalled SQLCipher open
            // would hold the broadcast past its budget with no way to time out.
            val database = databaseHolder.awaitOpen()
            val homeDao = database.homeDao()
            val medicineDao = database.medicineDao()
            val medicationLogDao = database.medicationLogDao()
            val activeGroupEntities = homeDao.getActiveGroups()
            val archivedGroupEntities = homeDao.getArchivedGroups()
            val scheduleEntryEntities = homeDao.getScheduleEntries(
                scheduledStartIso = snapshotWindow.bufferedScheduledStart.toString(),
                scheduledEndIso = snapshotWindow.bufferedScheduledEnd.toString(),
                manualStartEpochMillis = snapshotWindow.bufferedManualStartEpochMillis,
                manualEndEpochMillis = snapshotWindow.bufferedManualEndEpochMillis,
            )
            val antiandrogenHistoryEntities = homeDao.getLatestAntiandrogenEntriesOnOrBefore(
                onOrBeforeEpochMillis = snapshotWindow.onOrBeforeEpochMillis,
            )
            val pkEntries = homeDao.getEstradiolPkEntries(
                startEpochMillis = cacheWindow.inputStartEpochMillis,
                endEpochMillis = cacheWindow.windowEndEpochMillis,
            )
            val latestEstradiolEntryEntity = homeDao.getLatestEstradiolEntryOnOrBefore(
                onOrBeforeEpochMillis = cacheWindow.generatedAtEpochMillis,
            )
            val stockMedicineEntities = medicineDao.getAllActiveTrackedEntities()
            val stockWindowStartIso = now.toLocalDate()
                .minusDays(1)
                .atStartOfDay()
                .toString()
            val stockWindowEndIso = now.toLocalDate()
                .plusDays(ScheduledRunwayCalculator.HORIZON_DAYS)
                .atTime(23, 59, 59)
                .toString()
            val stockFulfillmentEntities = medicationLogDao.getScheduledEntriesInWindow(
                scheduledStartIso = stockWindowStartIso,
                scheduledEndIso = stockWindowEndIso,
            )
            val homeAnchor = database.journalDao().getFirstPinnedTrackedDate()?.toModel()
            // Calibration inputs: every E2 lab, every estradiol dose (not just
            // the chart window), and the user's review metadata.
            val calibrationLabs = database.bloodTestDao().getPanels().flatMap { panel ->
                panel.results
                    .filter { result ->
                        result.builtinAnalyteKey == BloodAnalyteKey.E2.storageValue
                    }
                    .map { result ->
                        PkCalibrationLab(
                            resultId = UUID.fromString(result.uuid),
                            collectedAtEpochMillis = panel.panel.collectedAtInstantEpochMillis,
                            valuePgml = result.canonicalValue,
                        )
                    }
            }
            val calibrationEntryEntities = homeDao.getEstradiolPkEntries(
                startEpochMillis = Long.MIN_VALUE,
                endEpochMillis = Long.MAX_VALUE,
            )
            val calibrationMetadata = database.pkCalibrationDao().getAllMetadata()
                .mapNotNull { entity -> entity.toModel() }

            val groupMedicinesByUuid = database.resolveMedicinesForGroups(
                activeGroupEntities + archivedGroupEntities
            )
            val entryMedicinesByUuid = database.resolveMedicinesForEntries(
                scheduleEntryEntities + antiandrogenHistoryEntities + pkEntries +
                        listOfNotNull(latestEstradiolEntryEntity) + stockFulfillmentEntities +
                        calibrationEntryEntities
            )
            val activeGroups = activeGroupEntities.map { group ->
                group.toMedicationGroupModel(groupMedicinesByUuid)
            }
            val archivedGroups = archivedGroupEntities.map { group ->
                group.toMedicationGroupModel(groupMedicinesByUuid)
            }
            val scheduleEntries = scheduleEntryEntities.map { entry ->
                entry.toMedicationLogEntryModel(entryMedicinesByUuid)
            }
            val antiandrogenHistoryEntries = antiandrogenHistoryEntities.map { entry ->
                entry.toMedicationLogEntryModel(entryMedicinesByUuid)
            }
            val pkEntryModels = pkEntries.map { entry ->
                entry.toMedicationLogEntryModel(entryMedicinesByUuid)
            }
            val latestEstradiolEntry = latestEstradiolEntryEntity?.toMedicationLogEntryModel(
                entryMedicinesByUuid
            )
            val stockMedicines = stockMedicineEntities.map { entity -> entity.toMedicineModel() }
            val stockFulfillmentEntries = stockFulfillmentEntities.map { entry ->
                entry.toMedicationLogEntryModel(entryMedicinesByUuid)
            }
            HomeSnapshotBuildInputs(
                calibrationLabs = calibrationLabs,
                calibrationEntries = calibrationEntryEntities.map { entry ->
                    entry.toMedicationLogEntryModel(entryMedicinesByUuid)
                },
                calibrationMetadata = calibrationMetadata,
                activeGroups = activeGroups,
                archivedGroups = archivedGroups,
                scheduleEntries = scheduleEntries,
                antiandrogenHistoryEntries = antiandrogenHistoryEntries,
                pkEntries = pkEntryModels,
                latestEstradiolEntry = latestEstradiolEntry,
                profile = database.userProfileDao()
                    .getProfile()
                    ?.toUserProfileModel()
                    ?: UserProfile(),
                stockMedicines = stockMedicines,
                stockFulfillmentEntries = stockFulfillmentEntries,
                homeAnchor = homeAnchor,
            )
        }
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_refresh_inputs_loaded generation=$refreshGeneration " +
                    "activeGroups=${inputs.activeGroups.size} " +
                    "archivedGroups=${inputs.archivedGroups.size} " +
                    "scheduleEntries=${inputs.scheduleEntries.size} " +
                    "antiandrogenEntries=${inputs.antiandrogenHistoryEntries.size} " +
                    "pkEntries=${inputs.pkEntries.size} " +
                    "hasLatestEstradiol=${inputs.latestEstradiolEntry != null}"
        )

        // Lab calibration is part of the snapshot so the calibrated curve is
        // what Home and the widget show first, not a population curve that a
        // later live evaluation swaps out.
        val calibrationInput = buildPkCalibrationInput(
            labs = inputs.calibrationLabs,
            entries = inputs.calibrationEntries,
            weightKg = inputs.profile.weightKg,
            metadata = inputs.calibrationMetadata,
            fallbackOriginEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
        )
        val calibration = calibrationInput?.let { input ->
            withContext(defaultDispatcher) { PkCalibrationEngine.evaluate(input) }
        }
        val personalParams = calibration?.result?.displayParams ?: PkPersonalParams.population()
        val horizon = now.toLocalDate().plusDays(option.projectionFutureDays()).atStartOfDay()
        val simulationEntries = buildEstradiolPkSimulationEntries(
            realEntries = inputs.pkEntries,
            activeGroups = inputs.activeGroups,
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )
        // Home chart includes planned future doses; the widget shows the current
        // body state from already-logged doses only, so its projection must
        // exclude `plannedEntries`. Otherwise a planned dose 30 min from now
        // would inflate the widget's "current" reading.
        val (projection, widgetProjection) = coroutineScope {
            val homeAsync = async(defaultDispatcher) {
                PkMedicationSimulation.simulateMainEstradiolProjection(
                    entries = simulationEntries.real,
                    plannedEntries = simulationEntries.planned,
                    bodyWeightKg = inputs.profile.weightKg,
                    generatedAt = now,
                    zoneId = zoneId,
                    option = option,
                    personalParams = personalParams,
                )
            }
            val widgetAsync = async(defaultDispatcher) {
                PkMedicationSimulation.simulateMainEstradiolProjection(
                    entries = simulationEntries.real,
                    plannedEntries = emptyList(),
                    bodyWeightKg = inputs.profile.weightKg,
                    generatedAt = now,
                    zoneId = zoneId,
                    option = option,
                    personalParams = personalParams,
                )
            }
            homeAsync.await() to widgetAsync.await()
        }
        // The band follows the same projected curve as the chart: logged plus
        // planned doses, over the projection window.
        val bandKnots = if (calibration == null || calibrationInput == null) {
            emptyList()
        } else {
            withContext(defaultDispatcher) {
                val anchor = Instant.ofEpochMilli(calibrationInput.originEpochMillis)
                val plannedEvents = simulationEntries.planned.mapNotNull { entry ->
                    entry.buildEstradiolPkDoseEvent(anchor, isPlanned = true)
                }
                PkChartDomain.create(
                    rangeStartEpochMillis = projection.windowStart.toEpochMilli(),
                    rangeEndEpochMillis = projection.windowEnd.toEpochMilli(),
                    samplingIntervalMillis = BAND_SAMPLING_INTERVAL_MILLIS,
                )?.let { domain ->
                    calibration.renderFor(domain, calibrationInput.doseEvents + plannedEvents)
                }?.takeIf { render -> render.bandState == PkCalibrationBandState.READY }
                    ?.bandKnots
                    ?.map { knot ->
                        HomePkBandKnotRecord(
                            epochMillis = knot.epochMillis,
                            p025Pgml = knot.p025Pgml,
                            p158655254Pgml = knot.p158655254Pgml,
                            p50Pgml = knot.p50Pgml,
                            p841344746Pgml = knot.p841344746Pgml,
                            p975Pgml = knot.p975Pgml,
                        )
                    }
                    .orEmpty()
            }
        }
        // Expire at the soonest planned dose after generation time — the
        // moment a slot transitions from "future" to "should be logged by now"
        // is exactly when a cached projection starts overstating the curve.
        // Fall back to the projection's window end when there are no future
        // planned slots (nothing to go stale).
        val expiresAtInstant = simulationEntries.planned.asSequence()
            .map { entry -> entry.appliedAt }
            .filter { instant -> instant.isAfter(projection.generatedAt) }
            .minOrNull()
            ?: projection.windowEnd
        diagnosticsLogger.info(
            TAG,
            "home_snapshot_refresh_projection_built generation=$refreshGeneration " +
                    "windowStart=${projection.windowStart} windowEnd=${projection.windowEnd} " +
                    "expiresAt=$expiresAtInstant"
        )
        val pkProjectionRecord = HomePkProjectionRecord(
            generatedAtEpochMillis = projection.generatedAt.toEpochMilli(),
            windowStartEpochMillis = projection.windowStart.toEpochMilli(),
            windowEndEpochMillis = projection.windowEnd.toEpochMilli(),
            pkProjectionExpiresAtEpochMillis = expiresAtInstant.toEpochMilli(),
            concentrationUnit = projection.concentrationUnit.name,
            timeH = projection.timeH,
            concentrations = projection.concentrations,
            doseMarkers = projection.doseMarkers.map { marker ->
                HomePkProjectionDoseMarkerRecord(
                    timeH = marker.timeH,
                    concentration = marker.concentration,
                    isPlanned = marker.isPlanned,
                )
            },
            latestEstradiolEntry = inputs.latestEstradiolEntry,
            chartWindowHours = option.chartWindowHours.toInt(),
            densePolicy = option.densePolicy.toRecord(),
            includesPostDoseOffsets = option.includesPostDoseOffsets,
        )
        // No planned doses → nothing to "go stale" mid-window; expire at windowEnd.
        val widgetPkProjectionRecord = HomePkProjectionRecord(
            generatedAtEpochMillis = widgetProjection.generatedAt.toEpochMilli(),
            windowStartEpochMillis = widgetProjection.windowStart.toEpochMilli(),
            windowEndEpochMillis = widgetProjection.windowEnd.toEpochMilli(),
            pkProjectionExpiresAtEpochMillis = widgetProjection.windowEnd.toEpochMilli(),
            concentrationUnit = widgetProjection.concentrationUnit.name,
            timeH = widgetProjection.timeH,
            concentrations = widgetProjection.concentrations,
            doseMarkers = widgetProjection.doseMarkers.map { marker ->
                HomePkProjectionDoseMarkerRecord(
                    timeH = marker.timeH,
                    concentration = marker.concentration,
                    isPlanned = marker.isPlanned,
                )
            },
            latestEstradiolEntry = inputs.latestEstradiolEntry,
            chartWindowHours = option.chartWindowHours.toInt(),
            densePolicy = option.densePolicy.toRecord(),
            includesPostDoseOffsets = option.includesPostDoseOffsets,
        )
        val snapshotRecord = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generation = refreshGeneration,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            pkProjection = pkProjectionRecord,
            widgetPkProjection = widgetPkProjectionRecord,
            activeGroups = inputs.activeGroups,
            archivedGroups = inputs.archivedGroups,
            scheduleEntries = inputs.scheduleEntries,
            antiandrogenHistoryEntries = inputs.antiandrogenHistoryEntries,
            userProfile = inputs.profile,
            stockMedicines = inputs.stockMedicines,
            stockFulfillmentEntries = inputs.stockFulfillmentEntries,
            pkEntries = inputs.pkEntries,
            homeAnchor = inputs.homeAnchor,
            pkRouteLogScale = personalParams.routeLogScale
                .mapKeys { (route, _) -> route.stableId },
            pkBandKnots = bandKnots,
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
        val calibrationLabs: List<PkCalibrationLab>,
        val calibrationEntries: List<MedicationLogEntry>,
        val calibrationMetadata: List<E2CalibrationMetadata>,
        val activeGroups: List<MedicationGroup>,
        val archivedGroups: List<MedicationGroup>,
        val scheduleEntries: List<MedicationLogEntry>,
        val antiandrogenHistoryEntries: List<MedicationLogEntry>,
        val pkEntries: List<MedicationLogEntry>,
        val latestEstradiolEntry: MedicationLogEntry?,
        val profile: UserProfile,
        val stockMedicines: List<Medicine>,
        val stockFulfillmentEntries: List<MedicationLogEntry>,
        val homeAnchor: TrackedDate? = null,
    )

    // Full projection-cache window: past-days back from today's midnight, plus
    // option.futureDays + MainChartProjectionFutureBufferDays of forward span.
    // Used by the refresh path to size simulator bounds and Room input queries.
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
                option: HomeE2ChartWindowOption,
            ): HomePkProjectionWindow {
                val generatedAt = now.atZone(zoneId).toInstant()
                val windowStart = now
                    .toLocalDate()
                    .atStartOfDay()
                    .minusDays(option.pastDays)
                    .atZone(zoneId)
                    .toInstant()
                val windowEnd = now
                    .toLocalDate()
                    .plusDays(option.projectionFutureDays())
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

    // Visible chart window the projection must cover for the selected option.
    // Used by validation; intentionally narrower than HomePkProjectionWindow so
    // a snapshot whose end is past the chart end (but inside the cache buffer)
    // is still usable.
    private data class HomePkChartWindow(
        val windowStartEpochMillis: Long,
        val windowEndEpochMillis: Long,
    ) {
        companion object {
            fun forNow(
                now: LocalDateTime,
                zoneId: ZoneId,
                option: HomeE2ChartWindowOption,
            ): HomePkChartWindow {
                val windowStart = now
                    .toLocalDate()
                    .atStartOfDay()
                    .minusDays(option.pastDays)
                    .atZone(zoneId)
                    .toInstant()
                val windowEnd = windowStart.plus(
                    Duration.ofHours(option.chartWindowHours)
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

    private suspend fun refreshHomeSnapshotBestEffort(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        runCatching {
            refreshHomeSnapshotIfNeeded(now = now, force = force, zoneId = zoneId)
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            diagnosticsLogger.warning(
                TAG,
                "home_snapshot_refresh_failed force=$force now=$now",
                throwable
            )
        }
    }

    private suspend fun refreshHomeSnapshotBestEffortLocked(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        runCatching {
            refreshHomeSnapshotIfNeededLocked(now = now, force = force, zoneId = zoneId)
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            diagnosticsLogger.warning(
                TAG,
                "home_snapshot_refresh_failed force=$force now=$now",
                throwable
            )
        }
    }

}

private fun HomeSnapshotRecord.diagnosticSummary(): String {
    return "schema=$schemaVersion " +
            "generation=$generation " +
            "anchorDate=${LocalDate.ofEpochDay(anchorDateEpochDay)} " +
            "zone=$zoneId " +
            "groups=${activeGroups.size} " +
            "archivedGroups=${archivedGroups.size} " +
            "scheduleEntries=${scheduleEntries.size} " +
            "antiandrogenEntries=${antiandrogenHistoryEntries.size} " +
            "hasPkProjection=${pkProjection != null}"
}

internal const val HOME_SNAPSHOT_SCHEMA_VERSION = 8

// Cache-input lookback past the visible chart window. 180 d is enough for
// steady-state PK history regardless of option; the forward span is owned
// by HomeE2ChartWindowOption.projectionFutureDays() and dropped its own
// constant.
private const val TAG = "HomeSnapshotRepository"
private const val HOME_PK_PROJECTION_LOOKBACK_DAYS = 180L
private const val BAND_SAMPLING_INTERVAL_MILLIS = 6L * 60L * 60L * 1_000L
private const val HOME_SCHEDULE_LOOKAHEAD_DAYS = 90L
private const val HOME_SNAPSHOT_VALIDITY_DAYS = 10L
private const val HOME_SNAPSHOT_PAST_BUFFER_DAYS = 1L
