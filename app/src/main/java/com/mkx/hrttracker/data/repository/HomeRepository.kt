package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
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
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    fun observeHomeInputs(now: LocalDateTime, zoneId: ZoneId): Flow<HomeInputs> {
        return channelFlow {
            val sourceMutex = Mutex()
            var roomStarted = false
            val snapshotJob = launch {
                observeHomeSnapshotInputs(now, zoneId).take(1).collect { snapshotInputs ->
                    sourceMutex.withLock {
                        if (!roomStarted) {
                            send(snapshotInputs)
                        }
                    }
                }
            }

            observeHomeRoomInputs(now, zoneId).collect { roomInputs ->
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
                    pkProjection = homeSnapshotRepository.decodeProjection(pkProjectionRecord, now, zoneId),
                    pkProjectionExpiresAt = pkProjectionRecord
                        ?.let { Instant.ofEpochMilli(it.pkProjectionExpiresAtEpochMillis) },
                    latestEstradiolEntry = pkProjectionRecord?.latestEstradiolEntry,
                    estradiolPkEntries = simulationEntries.real,
                    estradiolPkPlannedEntries = simulationEntries.planned,
                    stockWarnings = stockWarnings,
                    source = HomeInputSource.SNAPSHOT,
                    now = now,
                )
            }
            .catch { throwable ->
                diagnosticsLogger.warning(TAG, "home_snapshot_inputs_failed", throwable)
            }
            .flowOn(Dispatchers.IO)
    }

    private fun observeHomeRoomInputs(now: LocalDateTime, zoneId: ZoneId): Flow<HomeInputs> {
        val today = now.toLocalDate()
        val stockWindowStartIso = today.minusDays(1).atStartOfDay().toString()
        val stockWindowEndIso = today
            .plusDays(ScheduledRunwayCalculator.HORIZON_DAYS)
            .atTime(23, 59, 59)
            .toString()
        val nowInstant = now.atZone(zoneId).toInstant()
        val roomBasicsFlow = observeHomeStartupInputs(now, zoneId)
        return combine(
            roomBasicsFlow,
            homeSnapshotRepository.observeHomeSnapshot(),
            settingsRepository.homeE2ChartWindowOptionFlow,
            medicineRepository.observeAllActiveTracked(),
            medicationLogRepository.observeScheduledEntriesInWindow(
                scheduledStartIso = stockWindowStartIso,
                scheduledEndIso = stockWindowEndIso,
            ),
        ) { inputs, snapshot, option, trackedMedicines, stockFulfillmentEntries ->
            if (inputs.settings.homeE2ChartWindowOption != option) {
                return@combine null
            }
            val pkProjectionRecord = snapshot
                ?.takeIf {
                    homeSnapshotRepository.isSnapshotUsable(
                        snapshot = it,
                        now = now,
                        zoneId = zoneId,
                        option = option,
                    )
                }
                ?.pkProjection
            val stockWarnings = medicineStockRepository.projectAll(
                medicines = trackedMedicines,
                activeGroups = inputs.activeGroups,
                logEntries = stockFulfillmentEntries,
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
                pkProjection = homeSnapshotRepository.decodeProjection(pkProjectionRecord, now, zoneId),
                pkProjectionExpiresAt = pkProjectionRecord
                    ?.let { Instant.ofEpochMilli(it.pkProjectionExpiresAtEpochMillis) },
                latestEstradiolEntry = pkProjectionRecord?.latestEstradiolEntry ?: inputs.latestEstradiolEntry,
                estradiolPkEntries = inputs.estradiolPkEntries,
                estradiolPkPlannedEntries = inputs.estradiolPkPlannedEntries,
                stockWarnings = stockWarnings,
                source = HomeInputSource.ROOM,
                now = now,
            )
        }
            .mapNotNull { inputs -> inputs }
            .catch { throwable ->
                diagnosticsLogger.warning(TAG, "home_room_inputs_failed", throwable)
                // settingsRepository.settingsState is eager and may still hold
                // the SEVEN_DAYS placeholder on cold start, so re-read the raw
                // option flow for the fallback emission. The catch lambda is
                // a suspend context, so .first() is fine; wrap in try/catch so
                // a DataStore IO failure here doesn't escalate the emit.
                val fallbackOption: HomeE2ChartWindowOption = try {
                    settingsRepository.homeE2ChartWindowOptionFlow.first()
                } catch (innerThrowable: Throwable) {
                    if (innerThrowable is kotlinx.coroutines.CancellationException) throw innerThrowable
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
                        now = now,
                    )
                )
            }
            .flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHomeStartupInputs(now: LocalDateTime, zoneId: ZoneId): Flow<HomeStartupInputs> {
        val today = now.toLocalDate()
        val scheduledStartIso = today.minusDays(1).atStartOfDay().toString()
        val scheduledEndIso = today.plusDays(HOME_SCHEDULE_LOOKAHEAD_DAYS).atTime(23, 59, 59).toString()
        val manualStartEpochMillis = today.minusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        // Stable end bounds for the day so this flow doesn't re-subscribe per minute.
        // MainViewModel re-subscribes only on date change; a per-minute `now` upper
        // bound would otherwise hide entries the user logs later in the day.
        //
        // Two values, because the DAO predicates differ:
        //   - `manualEndEpochMillis`        : start of tomorrow, used with `<` (exclusive)
        //                                     by `observeScheduleEntries`.
        //   - `endOfTodayInclusiveEpochMillis`: last ms of today, used with `<=`
        //                                     (inclusive) by the `OnOrBefore` /
        //                                     `<=`-bounded queries so an entry stamped
        //                                     exactly at tomorrow 00:00 is NOT counted
        //                                     as "today's latest".
        //
        // The PK simulator filters future-applied entries internally; the single-row
        // "latest" queries (antiandrogen / estradiol) may surface a future-applied
        // entry if the user pre-logs one earlier today — accepted as a rare edge.
        val manualEndEpochMillis = today.plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val endOfTodayInclusiveEpochMillis = manualEndEpochMillis - 1L

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
                        val medicinesByUuid = database.resolveMedicinesForGroups(groups)
                        groups.map { it.toMedicationGroupModel(medicinesByUuid) }
                    }
                    val archivedGroupsFlow = combine(
                        homeDao.observeArchivedGroups(),
                        medicineChangeVersion,
                    ) { groups, _ ->
                        val medicinesByUuid = database.resolveMedicinesForGroups(groups)
                        groups.map { it.toMedicationGroupModel(medicinesByUuid) }
                    }
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
                            val medicinesByUuid = database.resolveMedicinesForEntries(entries)
                            entries.map { it.toMedicationLogEntryModel(medicinesByUuid) }
                        },
                        combine(
                            homeDao.observeLatestAntiandrogenEntriesOnOrBefore(
                                onOrBeforeEpochMillis = endOfTodayInclusiveEpochMillis,
                            ),
                            medicineChangeVersion,
                        ) { entries, _ ->
                            val medicinesByUuid = database.resolveMedicinesForEntries(entries)
                            entries.map { it.toMedicationLogEntryModel(medicinesByUuid) }
                        },
                        homeDao.observeProfile().map { profile ->
                            profile?.toUserProfileModel() ?: UserProfile()
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
                                val medicinesByUuid = database.resolveMedicinesForEntries(entries)
                                entries.map { it.toMedicationLogEntryModel(medicinesByUuid) }
                            },
                            combine(
                                homeDao.observeLatestEstradiolEntryOnOrBefore(
                                    onOrBeforeEpochMillis = endOfTodayInclusiveEpochMillis,
                                ),
                                medicineChangeVersion,
                            ) { entry, _ ->
                                val medicinesByUuid = database.resolveMedicinesForEntries(
                                    listOfNotNull(entry)
                                )
                                entry?.toMedicationLogEntryModel(medicinesByUuid)
                            },
                        ) { basics, realPkEntries, latestEstradiolEntry ->
                            val simulationEntries = buildEstradiolPkSimulationEntries(
                                realEntries = realPkEntries,
                                activeGroups = basics.activeGroups,
                                now = now,
                                horizon = pkHorizon,
                                zoneId = zoneId,
                            )
                            basics.copy(
                                estradiolPkEntries = simulationEntries.real,
                                estradiolPkPlannedEntries = simulationEntries.planned,
                                latestEstradiolEntry = latestEstradiolEntry,
                            )
                        }
                    )
                }
            }
            .catch { throwable ->
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
    val stockWarnings: List<MedicineStockProjection> = emptyList(),
    val source: HomeInputSource,
    val now: LocalDateTime,
)

enum class HomeInputSource {
    SNAPSHOT,
    ROOM,
}
