package com.mkx.hrttracker.data.repository

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.nextOccurrencesInPlanWindowFrom
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.pk.buildEstradiolPkSimulationEntries
import com.mkx.hrttracker.model.pk.projectionFutureDays
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.widget.updateAllHrtWidgets
import com.mkx.hrttracker.widget.WidgetDoseRow
import com.mkx.hrttracker.widget.WidgetDoseStatus
import com.mkx.hrttracker.reminder.medicationDisplayName
import com.mkx.hrttracker.reminder.medicationDoseText
import com.mkx.hrttracker.reminder.medicationRouteLabel
import com.mkx.hrttracker.ui.plan.PlanDayScheduleEntry
import com.mkx.hrttracker.ui.plan.buildPlanDaySchedule
import com.mkx.hrttracker.widget.WidgetDoseChip
import com.mkx.hrttracker.widget.WidgetPkDoseMarkerRecord
import com.mkx.hrttracker.widget.WidgetPkProjectionRecord
import com.mkx.hrttracker.widget.WidgetSnapshotRecord
import com.mkx.hrttracker.widget.WidgetSnapshotStore
import com.mkx.hrttracker.widget.WIDGET_SNAPSHOT_SCHEMA_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeSnapshotRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotStore: HomeSnapshotStore,
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore,
    private val widgetSnapshotStore: WidgetSnapshotStore,
    @param:ApplicationContext private val context: Context,
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
                    invalidateHomeSnapshot()
                    refreshHomeSnapshotAsync(force = true)
                }
            }
        }
        appScope.launch {
            var first = true
            settingsRepository.settingsState
                .map { s ->
                    listOf(
                        s.hideMedicationDetails,
                        s.adaptiveColorEnabled,
                        s.widgetContentScale,
                        s.widgetBackgroundAlpha,
                        s.darkModeOption,
                        s.appLanguageOption,
                    )
                }
                .distinctUntilChanged()
                .collect { _ ->
                    if (first) { first = false; return@collect }
                    diagnosticsLogger.info(TAG, "home_snapshot_widget_settings_changed")
                    refreshHomeSnapshotAsync(force = true)
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
                return
            }
            if (pkExpired) {
                diagnosticsLogger.info(
                    TAG,
                    "home_snapshot_refresh_continuing reason=pk_projection_expired " +
                        "${existingSnapshot?.diagnosticSummary() ?: "none"} now=$now"
                )
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
                endEpochMillis = cacheWindow.windowEndEpochMillis,
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

        val horizon = now.toLocalDate().plusDays(option.projectionFutureDays()).atStartOfDay()
        val simulationEntries = buildEstradiolPkSimulationEntries(
            realEntries = inputs.pkEntries,
            activeGroups = inputs.activeGroups,
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )
        val (projection, widgetProjectionResult) = coroutineScope {
            val homeAsync = async(defaultDispatcher) {
                PkMedicationSimulation.simulateMainEstradiolProjection(
                    entries = simulationEntries.real,
                    plannedEntries = simulationEntries.planned,
                    bodyWeightKg = inputs.profile.weightKg,
                    generatedAt = now,
                    zoneId = zoneId,
                    option = option,
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
                )
            }
            Pair(homeAsync.await(), widgetAsync.await())
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
        val widgetPkProjectionRecord = WidgetPkProjectionRecord(
            generatedAtEpochMillis = widgetProjectionResult.generatedAt.toEpochMilli(),
            windowStartEpochMillis = widgetProjectionResult.windowStart.toEpochMilli(),
            windowEndEpochMillis = widgetProjectionResult.windowEnd.toEpochMilli(),
            pkProjectionExpiresAtEpochMillis = expiresAtInstant.toEpochMilli(),
            concentrationUnit = widgetProjectionResult.concentrationUnit.name,
            timeH = widgetProjectionResult.timeH,
            concentrations = widgetProjectionResult.concentrations,
            doseMarkers = widgetProjectionResult.doseMarkers.map { marker ->
                WidgetPkDoseMarkerRecord(
                    timeH = marker.timeH,
                    concentration = marker.concentration,
                    isPlanned = marker.isPlanned,
                )
            },
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

        val settings = settingsRepository.getCurrentSettings()
        val widgetSnapshotRecord = buildWidgetSnapshotRecord(
            inputs = inputs,
            pkProjection = widgetPkProjectionRecord,
            now = now,
            zoneId = zoneId,
            hideMedicationDetails = settings.hideMedicationDetails,
            adaptiveColorEnabled = settings.adaptiveColorEnabled,
            widgetContentScale = settings.widgetContentScale,
            widgetBackgroundAlpha = settings.widgetBackgroundAlpha,
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
                    runCatching {
                        widgetSnapshotStore.writeSnapshot(widgetSnapshotRecord)
                    }.onFailure { e ->
                        diagnosticsLogger.warning(TAG, "widget_snapshot_write_failed", e)
                    }
                    runCatching { updateAllHrtWidgets(context) }.onFailure { throwable ->
                        diagnosticsLogger.warning(TAG, "widget_update_all_failed", throwable)
                    }
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
            widgetSnapshotStore.clearSnapshot()
            diagnosticsLogger.info(TAG, "home_snapshot_cleared_best_effort")
        }.onFailure { throwable ->
            diagnosticsLogger.warning(TAG, "home_snapshot_clear_failed", throwable)
        }
    }

    private fun buildWidgetSnapshotRecord(
        inputs: HomeSnapshotBuildInputs,
        pkProjection: WidgetPkProjectionRecord?,
        now: LocalDateTime,
        zoneId: ZoneId,
        hideMedicationDetails: Boolean,
        adaptiveColorEnabled: Boolean,
        widgetContentScale: Float,
        widgetBackgroundAlpha: Float,
    ): WidgetSnapshotRecord {
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)
        val comingUpEnd = today.plusDays(1).atTime(6, 0)

        val groupColorByUuid = inputs.activeGroups.associate { it.uuid to it.colorKey }

        // Last night rows (only during overnight hours: before 06:00)
        val isOvernight = now.toLocalTime().isBefore(LocalTime.of(6, 0))
        val lastNightRows: List<WidgetDoseRow> = if (isOvernight) {
            val yesterdaySchedule = buildPlanDaySchedule(
                date = yesterday,
                groups = inputs.activeGroups,
                entries = inputs.scheduleEntries,
                now = now,
                zoneId = zoneId,
            )
            val eveningCutoff = LocalTime.of(18, 0)
            val scheduledLastNight = yesterdaySchedule.scheduledEntries
                .filter { !it.scheduledFor.toLocalTime().isBefore(eveningCutoff) }
                .map { it.toWidgetDoseRow(context, WidgetDoseChip.LAST_NIGHT) }
            val manualLastNight = yesterdaySchedule.unplannedEntries
                .map { entry ->
                    val scheduledAt = entry.appliedAt.atZone(zoneId).toLocalDateTime()
                    WidgetDoseRow(
                        medicationName = medicationDisplayName(entry.details, context),
                        groupName = "",
                        colorKey = entry.sourceGroupUuid?.let { groupColorByUuid[it] },
                        routeLabel = medicationRouteLabel(entry.details, context),
                        doseText = medicationDoseText(context, entry.details) ?: "",
                        status = WidgetDoseStatus.DONE,
                        scheduledAt = scheduledAt,
                        trailingText = context.getString(R.string.plan_entry_label_manual),
                        isManualRecord = true,
                        contextChip = WidgetDoseChip.LAST_NIGHT,
                        groupUuid = null,
                        scheduleTimeUuid = null,
                    )
                }
                .filter { !it.scheduledAt.toLocalTime().isBefore(eveningCutoff) }
            (scheduledLastNight + manualLastNight).sortedBy { it.scheduledAt }
        } else emptyList()

        // Today scheduled and manual rows
        val todaySchedule = buildPlanDaySchedule(
            date = today,
            groups = inputs.activeGroups,
            entries = inputs.scheduleEntries,
            now = now,
            zoneId = zoneId,
        )
        val todayScheduledRows = todaySchedule.scheduledEntries
            .map { it.toWidgetDoseRow(context, null) }
        val manualRows = todaySchedule.unplannedEntries
            .map { entry ->
                val scheduledAt = entry.appliedAt.atZone(zoneId).toLocalDateTime()
                WidgetDoseRow(
                    medicationName = medicationDisplayName(entry.details, context),
                    groupName = "",
                    colorKey = entry.sourceGroupUuid?.let { groupColorByUuid[it] },
                    routeLabel = medicationRouteLabel(entry.details, context),
                    doseText = medicationDoseText(context, entry.details) ?: "",
                    status = WidgetDoseStatus.DONE,
                    scheduledAt = scheduledAt,
                    trailingText = context.getString(R.string.plan_entry_label_manual),
                    isManualRecord = true,
                    contextChip = null,
                    groupUuid = null,
                    scheduleTimeUuid = null,
                )
            }

        // Coming-up-next: only shown in evening (18:00–00:00), only entries before 06:00 tonight
        val isEvening = now.toLocalTime() >= LocalTime.of(18, 0)
        val comingUpRows: List<WidgetDoseRow> = if (isEvening) {
            val tomorrowSchedule = buildPlanDaySchedule(
                date = today.plusDays(1),
                groups = inputs.activeGroups,
                entries = inputs.scheduleEntries,
                now = now,
                zoneId = zoneId,
            )
            tomorrowSchedule.scheduledEntries
                .filter { it.scheduledFor.isBefore(comingUpEnd) }
                .map { it.toWidgetDoseRow(context, WidgetDoseChip.COMING_UP) }
        } else emptyList()

        val allRows = lastNightRows +
            (todayScheduledRows + manualRows).sortedBy { it.scheduledAt } +
            comingUpRows

        val doneCount = todayScheduledRows.count { it.status == WidgetDoseStatus.DONE }

        return WidgetSnapshotRecord(
            schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
            zoneId = zoneId.id,
            doneCount = doneCount,
            totalCount = todayScheduledRows.size,
            manualCount = manualRows.size,
            hideMedicationDetails = hideMedicationDetails,
            adaptiveColorEnabled = adaptiveColorEnabled,
            widgetContentScale = widgetContentScale,
            widgetBackgroundAlpha = widgetBackgroundAlpha,
            doseRows = allRows,
            pkProjection = pkProjection,
        )
    }
}

private fun PlanDayScheduleEntry.toWidgetDoseRow(
    context: Context,
    contextChip: WidgetDoseChip?,
): WidgetDoseRow {
    val status = when {
        isFulfilled -> WidgetDoseStatus.DONE
        hasOutsideScheduleWindowEntry -> WidgetDoseStatus.LOGGED_OUT_OF_WINDOW
        isPastDue -> WidgetDoseStatus.OVERDUE
        isDueSoon -> WidgetDoseStatus.DUE_SOON
        else -> WidgetDoseStatus.UPCOMING
    }
    val isActionable = status == WidgetDoseStatus.DUE_SOON || status == WidgetDoseStatus.OVERDUE
    val displayTime = when (status) {
        WidgetDoseStatus.DONE, WidgetDoseStatus.LOGGED_OUT_OF_WINDOW -> null
        else -> scheduledFor.format(timeFormatter)
    }
    return WidgetDoseRow(
        medicationName = medicationDisplayName(medication.details, context),
        groupName = groupName,
        colorKey = groupColorKey,
        routeLabel = medicationRouteLabel(medication.details, context),
        doseText = medicationDoseText(context, medication.details) ?: "",
        status = status,
        scheduledAt = scheduledFor,
        trailingText = displayTime,
        isManualRecord = false,
        contextChip = contextChip,
        groupUuid = if (isActionable) groupUuid.toString() else null,
        scheduleTimeUuid = if (isActionable) scheduleTimeUuid?.toString() else null,
        medicationUuid = medication.uuid.toString(),
    )
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

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal const val HOME_SNAPSHOT_SCHEMA_VERSION = 5

// Cache-input lookback past the visible chart window. 180 d is enough for
// steady-state PK history regardless of option; the forward span is owned
// by HomeE2ChartWindowOption.projectionFutureDays() and dropped its own
// constant.
private const val TAG = "HomeSnapshotRepository"
private const val HOME_PK_PROJECTION_LOOKBACK_DAYS = 180L
private const val HOME_SCHEDULE_LOOKAHEAD_DAYS = 90L
private const val HOME_SNAPSHOT_VALIDITY_DAYS = 10L
private const val HOME_SNAPSHOT_PAST_BUFFER_DAYS = 1L
private const val DUE_SOON_THRESHOLD_SECONDS = 7_200L
