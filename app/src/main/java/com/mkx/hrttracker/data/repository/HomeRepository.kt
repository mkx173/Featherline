package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.pk.PkPersonalParams
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.pk.buildEstradiolPkSimulationEntries
import com.mkx.hrttracker.model.pk.projectionFutureDays
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val settingsRepository: SettingsRepository,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val medicineStockRepository: MedicineStockRepository,
    private val medicineRepository: MedicineRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val journalRepository: JournalRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    fun observeHomeInputs(
        date: LocalDate,
        nowFlow: StateFlow<LocalDateTime>,
        zoneId: ZoneId,
    ): Flow<HomeInputs> {
        return channelFlow {
            val sourceMutex = Mutex()
            var roomStarted = false
            val snapshotJob = launch {
                observeHomeSnapshotInputs(nowFlow.value, zoneId).take(1).collect { snapshotInputs ->
                    sourceMutex.withLock {
                        if (!roomStarted) {
                            send(snapshotInputs)
                        }
                    }
                }
            }

            observeHomeRoomInputs(date, nowFlow, zoneId).collect { roomInputs ->
                sourceMutex.withLock {
                    if (!roomStarted) {
                        roomStarted = true
                        snapshotJob.cancel()
                    }
                    send(roomInputs)
                }
            }
        }
    }

    fun observeHomeSnapshotInputs(now: LocalDateTime, zoneId: ZoneId): Flow<HomeInputs> {
        // homeE2ChartWindowOptionFlow is the raw DataStore-backed flow that
        // bypasses settingsState's eager initialValue, so the first emission
        // here carries the persisted option rather than the SEVEN_DAYS
        // placeholder. Validating downstream of that emission keeps a
        // THIRTY_DAYS-fingerprinted snapshot from being rejected on cold start
        // just because settingsState hasn't loaded yet.
        return combine(
            homeSnapshotRepository.observeHomeSnapshot(),
            settingsRepository.homeE2ChartWindowOptionFlow,
            settingsRepository.settingsState,
        ) { snapshot, option, settingsState ->
            Triple(snapshot, option, settingsState)
        }
            .mapNotNull { (snapshot, option, settingsState) ->
                val usable = snapshot?.takeIf {
                    homeSnapshotRepository.isSnapshotUsable(
                        snapshot = it,
                        now = now,
                        zoneId = zoneId,
                        option = option,
                    )
                } ?: return@mapNotNull null
                val pkProjectionRecord = usable.pkProjection
                // Rebuild the simulator inputs from the embedded PK log entries so
                // the snapshot path can recompute the curve locally (no Room) when
                // the cached projection has expired — e.g. a missed planned dose.
                // `buildEstradiolPkSimulationEntries` is pure, preserving the
                // snapshot path's "never opens the database" guarantee.
                val pkHorizon = now.toLocalDate()
                    .plusDays(option.projectionFutureDays())
                    .atStartOfDay()
                val simulationEntries = buildEstradiolPkSimulationEntries(
                    realEntries = usable.pkEntries,
                    activeGroups = usable.activeGroups,
                    now = now,
                    horizon = pkHorizon,
                    zoneId = zoneId,
                )
                val stockWarnings = medicineStockRepository.projectAll(
                    medicines = usable.stockMedicines,
                    activeGroups = usable.activeGroups,
                    logEntries = usable.stockFulfillmentEntries,
                    now = now.atZone(zoneId).toInstant(),
                    zoneId = zoneId,
                ).stockWarningsOnly()
                HomeInputs(
                    activeGroups = usable.activeGroups,
                    archivedGroups = usable.archivedGroups,
                    scheduleEntries = homeSnapshotRepository.scheduleEntriesForHome(
                        snapshot = usable,
                        now = now,
                        zoneId = zoneId,
                    ),
                    antiandrogenHistoryEntries = usable.antiandrogenHistoryEntries,
                    profile = UserProfile(),
                    // settingsState's eager placeholder could disagree with the raw
                    // option flow on cold start; copy the raw value in so projection
                    // consumers downstream see the same option the validator approved.
                    settings = settingsState.copy(homeE2ChartWindowOption = option),
                    pkProjection = homeSnapshotRepository.decodeProjection(
                        pkProjectionRecord,
                        now,
                        zoneId
                    ),
                    pkProjectionExpiresAt = pkProjectionRecord
                        ?.let { Instant.ofEpochMilli(it.pkProjectionExpiresAtEpochMillis) },
                    latestEstradiolEntry = pkProjectionRecord?.latestEstradiolEntry,
                    estradiolPkEntries = simulationEntries.real,
                    estradiolPkPlannedEntries = simulationEntries.planned,
                    pkPersonalParams = usable.pkPersonalParams(),
                    pkBandKnots = usable.pkBandKnots(),
                    stockWarnings = stockWarnings,
                    homeAnchor = usable.homeAnchor,
                    source = HomeInputSource.SNAPSHOT,
                    now = now,
                )
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                diagnosticsLogger.warning(TAG, "home_snapshot_inputs_failed", throwable)
            }
            .flowOn(Dispatchers.IO)
    }

    private fun observeHomeRoomInputs(
        date: LocalDate,
        nowFlow: StateFlow<LocalDateTime>,
        zoneId: ZoneId,
    ): Flow<HomeInputs> {
        val today = date
        val stockWindowStartIso = today.minusDays(1).atStartOfDay().toString()
        val stockWindowEndIso = today
            .plusDays(ScheduledRunwayCalculator.HORIZON_DAYS)
            .atTime(23, 59, 59)
            .toString()
        val roomBasicsFlow = observeHomeStartupInputs(nowFlow.value, zoneId)
        val stockInputsFlow = combine(
            medicineRepository.observeAllActiveTracked(),
            medicationLogRepository.observeScheduledEntriesInWindow(
                scheduledStartIso = stockWindowStartIso,
                scheduledEndIso = stockWindowEndIso,
            ),
        ) { trackedMedicines, stockFulfillmentEntries ->
            trackedMedicines to stockFulfillmentEntries
        }
        val stockAndAnchorInputsFlow = combine(
            stockInputsFlow,
            journalRepository.observePinnedTrackedDates()
                .map { pinnedDates -> pinnedDates.firstOrNull() }
                .distinctUntilChanged(),
        ) { stockInputs, homeAnchor ->
            val (trackedMedicines, stockFulfillmentEntries) = stockInputs
            HomeRoomStockAndAnchorInputs(
                trackedMedicines = trackedMedicines,
                stockFulfillmentEntries = stockFulfillmentEntries,
                homeAnchor = homeAnchor,
            )
        }
        return combine(
            roomBasicsFlow,
            homeSnapshotRepository.observeHomeSnapshot(),
            settingsRepository.homeE2ChartWindowOptionFlow,
            stockAndAnchorInputsFlow,
            nowFlow,
        ) { inputs, snapshot, option, stockAndAnchorInputs, now ->
            suppressInconsistentHomeEmission("room_inputs") {
                if (inputs.settings.homeE2ChartWindowOption != option) {
                    null
                } else {
                    val nowInstant = now.atZone(zoneId).toInstant()
                    val pkHorizon = date
                        .plusDays(option.projectionFutureDays())
                        .atStartOfDay()
                    val simulationEntries = buildEstradiolPkSimulationEntries(
                        realEntries = inputs.estradiolPkEntries,
                        activeGroups = inputs.activeGroups,
                        now = now,
                        horizon = pkHorizon,
                        zoneId = zoneId,
                    )
                    val usableSnapshot = snapshot?.takeIf {
                        homeSnapshotRepository.isSnapshotUsable(
                            snapshot = it,
                            now = now,
                            zoneId = zoneId,
                            option = option,
                        )
                    }
                    val pkProjectionRecord = usableSnapshot?.pkProjection
                    val stockWarnings = medicineStockRepository.projectAll(
                        medicines = stockAndAnchorInputs.trackedMedicines,
                        activeGroups = inputs.activeGroups,
                        logEntries = stockAndAnchorInputs.stockFulfillmentEntries,
                        now = nowInstant,
                        zoneId = zoneId,
                    ).stockWarningsOnly()
                    HomeInputs(
                        activeGroups = inputs.activeGroups,
                        archivedGroups = inputs.archivedGroups,
                        scheduleEntries = inputs.scheduleEntries,
                        antiandrogenHistoryEntries = inputs.antiandrogenHistoryEntries,
                        profile = inputs.profile,
                        // The outer combine already pinned `option` to the raw flow's
                        // latest value, but `inputs.settings` came from the inner
                        // startup combine, which may briefly carry a stale option if
                        // the option flow emits before the basicsFlow re-emits. Copy
                        // the raw option in so consumers downstream cannot see the
                        // outer flow's option disagree with HomeInputs.settings.
                        settings = inputs.settings.copy(homeE2ChartWindowOption = option),
                        pkProjection = homeSnapshotRepository.decodeProjection(
                            pkProjectionRecord,
                            now,
                            zoneId
                        ),
                        pkProjectionExpiresAt = pkProjectionRecord
                            ?.let { Instant.ofEpochMilli(it.pkProjectionExpiresAtEpochMillis) },
                        latestEstradiolEntry = pkProjectionRecord?.latestEstradiolEntry
                            ?: inputs.latestEstradiolEntry,
                        estradiolPkEntries = simulationEntries.real,
                        estradiolPkPlannedEntries = simulationEntries.planned,
                        pkPersonalParams = usableSnapshot?.pkPersonalParams()
                            ?: PkPersonalParams.population(),
                        pkBandKnots = usableSnapshot?.pkBandKnots().orEmpty(),
                        stockWarnings = stockWarnings,
                        homeAnchor = stockAndAnchorInputs.homeAnchor,
                        source = HomeInputSource.ROOM,
                        now = now,
                    )
                }
            }
        }
            .mapNotNull { inputs -> inputs }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                diagnosticsLogger.warning(TAG, "home_room_inputs_failed", throwable)
                // settingsRepository.settingsState is eager and may still hold
                // the SEVEN_DAYS placeholder on cold start, so re-read the raw
                // option flow for the fallback emission. The catch lambda is
                // a suspend context, so .first() is fine; wrap in try/catch so
                // a DataStore IO failure here doesn't escalate the emit.
                val fallbackOption: HomeE2ChartWindowOption = try {
                    settingsRepository.homeE2ChartWindowOptionFlow.first()
                } catch (innerThrowable: Throwable) {
                    if (innerThrowable is CancellationException) throw innerThrowable
                    HomeE2ChartWindowOption.SEVEN_DAYS
                }
                val fallbackSettings = settingsRepository.settingsState.value
                    .copy(homeE2ChartWindowOption = fallbackOption)
                emit(
                    HomeInputs(
                        activeGroups = emptyList(),
                        scheduleEntries = emptyList(),
                        antiandrogenHistoryEntries = emptyList(),
                        profile = UserProfile(),
                        settings = fallbackSettings,
                        pkProjection = null,
                        pkProjectionExpiresAt = null,
                        latestEstradiolEntry = null,
                        estradiolPkEntries = emptyList(),
                        source = HomeInputSource.ROOM,
                        now = nowFlow.value,
                    )
                )
            }
            .flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHomeStartupInputs(now: LocalDateTime, zoneId: ZoneId): Flow<HomeStartupInputs> {
        val today = now.toLocalDate()
        val scheduledStartIso = today.minusDays(1).atStartOfDay().toString()
        val scheduledEndIso =
            today.plusDays(HOME_SCHEDULE_LOOKAHEAD_DAYS).atTime(23, 59, 59).toString()
        val manualStartEpochMillis = today.minusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        // Stable end bound for the multi-row schedule window so this flow doesn't
        // re-subscribe per minute. MainViewModel re-subscribes only on date change;
        // a per-minute `now` upper bound would otherwise hide entries the user logs
        // later in the day from that window. `manualEndEpochMillis` is the start of
        // tomorrow, used with `<` (exclusive) by `observeScheduleEntries`.
        val manualEndEpochMillis = today.plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        // The antiandrogen and estradiol "latest" lookups each return a single row
        // (the most recent dose on or before the bound). Bound them at the current
        // snapshot time, not end-of-today, so a dose pre-logged for later today
        // doesn't become that single row and hide an earlier real dose. Doses the
        // user logs later today still surface via the schedule window above; these
        // lookups only provide the older-than-the-window fallback. This matches the
        // snapshot path, which bounds the same lookups at generation time.
        val latestEntryCutoffEpochMillis = now
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        // PK lookback and horizon depend on the selected chart-window option;
        // flatMapLatest re-subscribes the Room PK query when the user toggles
        // 7-day/30-day so the Room fallback sees the same dose horizon the
        // snapshot path simulates over.
        return settingsRepository.homeE2ChartWindowOptionFlow
            .flatMapLatest { option ->
                val pkStartEpochMillis = today
                    .atStartOfDay()
                    .minusDays(option.pastDays + HOME_PK_FALLBACK_LOOKBACK_DAYS)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
                val pkHorizon = today
                    .plusDays(option.projectionFutureDays())
                    .atStartOfDay()
                val pkEndEpochMillis = pkHorizon.atZone(zoneId).toInstant().toEpochMilli()

                flow {
                    val database = databaseHolder.get()
                    val homeDao = database.homeDao()
                    val medicineDao = database.medicineDao()
                    // Bound to each Home-table observation so medicine edits
                    // (displayName, archive, etc.) re-resolve the joined
                    // Medicine projection without waiting for the primary
                    // table to also change.
                    val medicineChangeVersion = medicineDao.observeMedicineChangeVersion()
                    val activeGroupsFlow = combine(
                        homeDao.observeActiveGroups(),
                        medicineChangeVersion,
                    ) { groups, _ ->
                        suppressInconsistentHomeEmission("active_groups") {
                            val medicinesByUuid = database.resolveMedicinesForGroups(groups)
                            groups.map { it.toMedicationGroupModel(medicinesByUuid) }
                        }
                    }.filterNotNull()
                    val archivedGroupsFlow = combine(
                        homeDao.observeArchivedGroups(),
                        medicineChangeVersion,
                    ) { groups, _ ->
                        suppressInconsistentHomeEmission("archived_groups") {
                            val medicinesByUuid = database.resolveMedicinesForGroups(groups)
                            groups.map { it.toMedicationGroupModel(medicinesByUuid) }
                        }
                    }.filterNotNull()
                    // Pair active + archived groups in a nested combine so the
                    // outer combine stays at five distinctly-typed flows; Kotlin
                    // only has typed combine overloads up to five flows (the
                    // six-flow form is the same-type vararg and breaks inference).
                    val groupsFlow = combine(
                        activeGroupsFlow,
                        archivedGroupsFlow,
                    ) { activeGroups, archivedGroups ->
                        activeGroups to archivedGroups
                    }
                    val basicsFlow = combine(
                        groupsFlow,
                        combine(
                            homeDao.observeScheduleEntries(
                                scheduledStartIso = scheduledStartIso,
                                scheduledEndIso = scheduledEndIso,
                                manualStartEpochMillis = manualStartEpochMillis,
                                manualEndEpochMillis = manualEndEpochMillis,
                            ),
                            medicineChangeVersion,
                        ) { entries, _ ->
                            suppressInconsistentHomeEmission("schedule_entries") {
                                val medicinesByUuid = database.resolveMedicinesForEntries(entries)
                                entries.map { it.toMedicationLogEntryModel(medicinesByUuid) }
                            }
                        }.filterNotNull(),
                        combine(
                            homeDao.observeLatestAntiandrogenEntriesOnOrBefore(
                                onOrBeforeEpochMillis = latestEntryCutoffEpochMillis,
                            ),
                            medicineChangeVersion,
                        ) { entries, _ ->
                            suppressInconsistentHomeEmission("antiandrogen_entries") {
                                val medicinesByUuid = database.resolveMedicinesForEntries(entries)
                                entries.map { it.toMedicationLogEntryModel(medicinesByUuid) }
                            }
                        }.filterNotNull(),
                        homeDao.observeProfile().mapNotNull { profile ->
                            suppressInconsistentHomeEmission("profile") {
                                profile?.toUserProfileModel() ?: UserProfile()
                            }
                        },
                        settingsRepository.settingsState,
                    ) { (activeGroups, archivedGroups), scheduleEntries, antiandrogenHistoryEntries, profile, settingsState ->
                        HomeStartupInputs(
                            activeGroups = activeGroups,
                            archivedGroups = archivedGroups,
                            scheduleEntries = scheduleEntries,
                            antiandrogenHistoryEntries = antiandrogenHistoryEntries,
                            profile = profile,
                            // Copy the raw option in so consumers downstream see the
                            // persisted choice even before settingsState's eager
                            // SEVEN_DAYS placeholder has resolved on cold start.
                            settings = settingsState.copy(homeE2ChartWindowOption = option),
                            estradiolPkEntries = emptyList(),
                            latestEstradiolEntry = null,
                        )
                    }
                    emitAll(
                        combine(
                            basicsFlow,
                            combine(
                                homeDao.observeEstradiolPkEntries(
                                    startEpochMillis = pkStartEpochMillis,
                                    endEpochMillis = pkEndEpochMillis,
                                ),
                                medicineChangeVersion,
                            ) { entries, _ ->
                                suppressInconsistentHomeEmission("pk_entries") {
                                    val medicinesByUuid =
                                        database.resolveMedicinesForEntries(entries)
                                    entries.map { it.toMedicationLogEntryModel(medicinesByUuid) }
                                }
                            }.filterNotNull(),
                            combine(
                                homeDao.observeLatestEstradiolEntryOnOrBefore(
                                    onOrBeforeEpochMillis = latestEntryCutoffEpochMillis,
                                ),
                                medicineChangeVersion,
                            ) { entry, _ ->
                                suppressInconsistentHomeEmission("latest_estradiol_entry") {
                                    val medicinesByUuid = database.resolveMedicinesForEntries(
                                        listOfNotNull(entry)
                                    )
                                    LatestEstradiolEntryEmission(
                                        entry?.toMedicationLogEntryModel(medicinesByUuid)
                                    )
                                }
                            }.filterNotNull(),
                        ) { basics, realPkEntries, latestEstradiolEntry ->
                            basics.copy(
                                estradiolPkEntries = realPkEntries,
                                estradiolPkPlannedEntries = emptyList(),
                                latestEstradiolEntry = latestEstradiolEntry.entry,
                            )
                        }
                    )
                }
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                diagnosticsLogger.warning(TAG, "home_startup_inputs_failed", throwable)
                emit(
                    HomeStartupInputs(
                        activeGroups = emptyList(),
                        scheduleEntries = emptyList(),
                        antiandrogenHistoryEntries = emptyList(),
                        profile = UserProfile(),
                        settings = settingsRepository.settingsState.value,
                        estradiolPkEntries = emptyList(),
                        latestEstradiolEntry = null,
                    )
                )
            }
            .flowOn(Dispatchers.IO)
    }

    fun refreshHomeSnapshotAsync(
        now: LocalDateTime,
        force: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        homeSnapshotRepository.refreshHomeSnapshotAsync(now = now, force = force, zoneId = zoneId)
    }

    private suspend fun <T> suppressInconsistentHomeEmission(
        site: String,
        block: suspend () -> T,
    ): T? {
        return try {
            block()
        } catch (error: Exception) {
            // During restore, Room can pair an old Home-table emission with an
            // already changed medicine table. SUPPRESS that inconsistent emission
            // (null -> filterNotNull/mapNotNull at the call site) so the next Room
            // invalidation recovers — a fabricated default (empty list / blank
            // profile) would reach the Home combine and render a one-frame wrong
            // state before the consistent emission lands. Catch Exception (not
            // Throwable) so Errors — and CancellationException — still propagate
            // rather than being hidden as a suppression.
            if (error is CancellationException) throw error
            diagnosticsLogger.warning(TAG, "home_emission_suppressed site=$site", error)
            null
        }
    }

    private companion object {
        const val TAG = "HomeRepository"
        const val HOME_SCHEDULE_LOOKAHEAD_DAYS = 90L

        // 180 d is enough back-history for steady-state PK regardless of the
        // selected chart window. The forward horizon is owned by
        // HomeE2ChartWindowOption.projectionFutureDays() so the Room fallback
        // and the cached snapshot share the same prediction span.
        const val HOME_PK_FALLBACK_LOOKBACK_DAYS = 180L
    }
}

// Wrapper so the latest-estradiol-entry flow can distinguish a legitimate
// "no entry" (entry == null) from a suppressed inconsistent emission
// (the wrapper itself filtered out as null).
private data class LatestEstradiolEntryEmission(val entry: MedicationLogEntry?)

private data class HomeRoomStockAndAnchorInputs(
    val trackedMedicines: List<com.mkx.hrttracker.model.medication.Medicine>,
    val stockFulfillmentEntries: List<MedicationLogEntry>,
    val homeAnchor: TrackedDate?,
)

private fun List<MedicineStockProjection>.stockWarningsOnly(): List<MedicineStockProjection> {
    return filter { projection ->
        projection.state == MedicineStockState.OUT ||
                projection.state == MedicineStockState.IMMINENT ||
                projection.state == MedicineStockState.USER_LOW
    }
}

data class HomeStartupInputs(
    val activeGroups: List<com.mkx.hrttracker.model.medication.MedicationGroup>,
    val archivedGroups: List<com.mkx.hrttracker.model.medication.MedicationGroup> = emptyList(),
    val scheduleEntries: List<MedicationLogEntry>,
    val antiandrogenHistoryEntries: List<MedicationLogEntry>,
    val profile: UserProfile,
    val settings: SettingsState,
    val estradiolPkEntries: List<MedicationLogEntry>,
    val estradiolPkPlannedEntries: List<MedicationLogEntry> = emptyList(),
    val latestEstradiolEntry: MedicationLogEntry?,
)

data class HomeInputs(
    val activeGroups: List<com.mkx.hrttracker.model.medication.MedicationGroup>,
    val archivedGroups: List<com.mkx.hrttracker.model.medication.MedicationGroup> = emptyList(),
    val scheduleEntries: List<MedicationLogEntry>,
    val antiandrogenHistoryEntries: List<MedicationLogEntry>,
    val profile: UserProfile,
    val settings: SettingsState,
    val pkProjection: PkProjectionResult?,
    // Wall-clock instant past which the cached pkProjection is stale and the
    // consumer should fall back to recomputing from `estradiolPkEntries` +
    // `estradiolPkPlannedEntries`. `decodeProjection` rejects expired records
    // at subscription time (against `now`-at-subscribe), but the live `now`
    // ticks per minute while the flow stays subscribed — consumers must
    // re-check against the live `now` to catch mid-session expiry.
    val pkProjectionExpiresAt: Instant?,
    val latestEstradiolEntry: MedicationLogEntry?,
    val estradiolPkEntries: List<MedicationLogEntry>,
    val estradiolPkPlannedEntries: List<MedicationLogEntry> = emptyList(),
    /** Calibration the cached projection used; local re-simulation must use the same. */
    val pkPersonalParams: PkPersonalParams = PkPersonalParams.population(),
    /** Band over the cached projection; valid exactly as long as the projection is. */
    val pkBandKnots: List<PkPredictiveBandKnot> = emptyList(),
    val stockWarnings: List<MedicineStockProjection> = emptyList(),
    val homeAnchor: TrackedDate? = null,
    val source: HomeInputSource,
    val now: LocalDateTime,
)

enum class HomeInputSource {
    SNAPSHOT,
    ROOM,
}
