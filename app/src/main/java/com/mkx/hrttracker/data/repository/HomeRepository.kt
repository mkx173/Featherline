package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.pk.buildEstradiolPkSimulationEntries
import com.mkx.hrttracker.model.pk.projectionFutureDays
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
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
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    fun observeHomeInputs(now: LocalDateTime): Flow<HomeInputs> {
        return channelFlow {
            val sourceMutex = Mutex()
            var roomStarted = false
            val snapshotJob = launch {
                observeHomeSnapshotInputs(now).take(1).collect { snapshotInputs ->
                    sourceMutex.withLock {
                        if (!roomStarted) {
                            send(snapshotInputs)
                        }
                    }
                }
            }

            observeHomeRoomInputs(now).collect { roomInputs ->
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

    fun observeHomeSnapshotInputs(now: LocalDateTime): Flow<HomeInputs> {
        val zoneId = ZoneId.systemDefault()
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
                HomeInputs(
                    activeGroups = usable.activeGroups,
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
                    estradiolPkEntries = emptyList(),
                    source = HomeInputSource.SNAPSHOT,
                    now = now,
                )
            }
            .catch { throwable ->
                diagnosticsLogger.warning(TAG, "home_snapshot_inputs_failed", throwable)
            }
            .flowOn(Dispatchers.IO)
    }

    private fun observeHomeRoomInputs(now: LocalDateTime): Flow<HomeInputs> {
        val zoneId = ZoneId.systemDefault()
        val roomBasicsFlow = observeHomeStartupInputs(now)
        return combine(
            roomBasicsFlow,
            homeSnapshotRepository.observeHomeSnapshot(),
            settingsRepository.homeE2ChartWindowOptionFlow,
        ) { inputs, snapshot, option ->
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
            HomeInputs(
                activeGroups = inputs.activeGroups,
                scheduleEntries = inputs.scheduleEntries,
                antiandrogenHistoryEntries = inputs.antiandrogenHistoryEntries,
                profile = inputs.profile,
                settings = inputs.settings,
                pkProjection = homeSnapshotRepository.decodeProjection(pkProjectionRecord, now, zoneId),
                pkProjectionExpiresAt = pkProjectionRecord
                    ?.let { Instant.ofEpochMilli(it.pkProjectionExpiresAtEpochMillis) },
                latestEstradiolEntry = pkProjectionRecord?.latestEstradiolEntry ?: inputs.latestEstradiolEntry,
                estradiolPkEntries = inputs.estradiolPkEntries,
                estradiolPkPlannedEntries = inputs.estradiolPkPlannedEntries,
                source = HomeInputSource.ROOM,
                now = now,
            )
        }
            .catch { throwable ->
                diagnosticsLogger.warning(TAG, "home_room_inputs_failed", throwable)
                emit(
                    HomeInputs(
                        activeGroups = emptyList(),
                        scheduleEntries = emptyList(),
                        antiandrogenHistoryEntries = emptyList(),
                        profile = UserProfile(),
                        settings = settingsRepository.settingsState.value,
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

    fun observeHomeStartupInputs(now: LocalDateTime): Flow<HomeStartupInputs> {
        val zoneId = ZoneId.systemDefault()
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
                    val homeDao = databaseHolder.get().homeDao()
                    val basicsFlow = combine(
                        homeDao.observeActiveGroups()
                            .map { groups -> groups.map { it.toMedicationGroupModel() } },
                        homeDao.observeScheduleEntries(
                            scheduledStartIso = scheduledStartIso,
                            scheduledEndIso = scheduledEndIso,
                            manualStartEpochMillis = manualStartEpochMillis,
                            manualEndEpochMillis = manualEndEpochMillis,
                        ).map { entries ->
                            entries.map { it.toMedicationLogEntryModel() }
                        },
                        homeDao.observeLatestAntiandrogenEntriesOnOrBefore(
                            onOrBeforeEpochMillis = endOfTodayInclusiveEpochMillis,
                        ).map { entries ->
                            entries.map { it.toMedicationLogEntryModel() }
                        },
                        homeDao.observeProfile().map { profile ->
                            profile?.toUserProfileModel() ?: UserProfile()
                        },
                        settingsRepository.settingsState,
                    ) { activeGroups, scheduleEntries, antiandrogenHistoryEntries, profile, settingsState ->
                        HomeStartupInputs(
                            activeGroups = activeGroups,
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
                            homeDao.observeEstradiolPkEntries(
                                startEpochMillis = pkStartEpochMillis,
                                endEpochMillis = pkEndEpochMillis,
                            ).map { entries ->
                                entries.map { it.toMedicationLogEntryModel() }
                            },
                            homeDao.observeLatestEstradiolEntryOnOrBefore(
                                onOrBeforeEpochMillis = endOfTodayInclusiveEpochMillis,
                            ).map { entry ->
                                entry?.toMedicationLogEntryModel()
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
    ) {
        homeSnapshotRepository.refreshHomeSnapshotAsync(now = now, force = force)
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

data class HomeStartupInputs(
    val activeGroups: List<com.mkx.hrttracker.model.medication.MedicationGroup>,
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
    val source: HomeInputSource,
    val now: LocalDateTime,
)

enum class HomeInputSource {
    SNAPSHOT,
    ROOM,
}
