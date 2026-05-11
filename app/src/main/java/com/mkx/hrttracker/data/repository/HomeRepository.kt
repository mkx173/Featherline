package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        return combine(
            homeSnapshotRepository.observeHomeSnapshot()
                .mapNotNull { snapshot ->
                    snapshot?.takeIf {
                        homeSnapshotRepository.isSnapshotUsable(
                            snapshot = it,
                            now = now,
                            zoneId = zoneId,
                        )
                    }
                },
            settingsRepository.settingsState,
        ) { snapshot, settingsState ->
            val pkProjectionRecord = snapshot.pkProjection
            HomeInputs(
                activeGroups = snapshot.activeGroups,
                scheduleEntries = homeSnapshotRepository.scheduleEntriesForHome(
                    snapshot = snapshot,
                    now = now,
                    zoneId = zoneId,
                ),
                antiandrogenHistoryEntries = snapshot.antiandrogenHistoryEntries,
                profile = UserProfile(),
                settings = settingsState,
                pkProjection = homeSnapshotRepository.decodeProjection(pkProjectionRecord),
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
        ) { inputs, snapshot ->
            val pkProjectionRecord = snapshot
                ?.takeIf {
                    homeSnapshotRepository.isSnapshotUsable(
                        snapshot = it,
                        now = now,
                        zoneId = zoneId,
                    )
                }
                ?.pkProjection
            HomeInputs(
                activeGroups = inputs.activeGroups,
                scheduleEntries = inputs.scheduleEntries,
                antiandrogenHistoryEntries = inputs.antiandrogenHistoryEntries,
                profile = inputs.profile,
                settings = inputs.settings,
                pkProjection = homeSnapshotRepository.decodeProjection(pkProjectionRecord),
                latestEstradiolEntry = pkProjectionRecord?.latestEstradiolEntry ?: inputs.latestEstradiolEntry,
                estradiolPkEntries = inputs.estradiolPkEntries,
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
        val pkStartEpochMillis = today
            .atStartOfDay()
            .minusDays(PkMedicationSimulation.mainChartPastDays + HOME_PK_FALLBACK_LOOKBACK_DAYS)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        return flow {
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
                    settings = settingsState,
                    estradiolPkEntries = emptyList(),
                    latestEstradiolEntry = null,
                )
            }
            emitAll(
                combine(
                    basicsFlow,
                    homeDao.observeEstradiolPkEntries(
                        startEpochMillis = pkStartEpochMillis,
                        endEpochMillis = endOfTodayInclusiveEpochMillis,
                    ).map { entries ->
                        entries.map { it.toMedicationLogEntryModel() }
                    },
                    homeDao.observeLatestEstradiolEntryOnOrBefore(
                        onOrBeforeEpochMillis = endOfTodayInclusiveEpochMillis,
                    ).map { entry ->
                        entry?.toMedicationLogEntryModel()
                    },
                ) { basics, estradiolPkEntries, latestEstradiolEntry ->
                    basics.copy(
                        estradiolPkEntries = estradiolPkEntries,
                        latestEstradiolEntry = latestEstradiolEntry,
                    )
                }
            )
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
    val latestEstradiolEntry: MedicationLogEntry?,
)

data class HomeInputs(
    val activeGroups: List<com.mkx.hrttracker.model.medication.MedicationGroup>,
    val scheduleEntries: List<MedicationLogEntry>,
    val antiandrogenHistoryEntries: List<MedicationLogEntry>,
    val profile: UserProfile,
    val settings: SettingsState,
    val pkProjection: PkProjectionResult?,
    val latestEstradiolEntry: MedicationLogEntry?,
    val estradiolPkEntries: List<MedicationLogEntry>,
    val source: HomeInputSource,
    val now: LocalDateTime,
)

enum class HomeInputSource {
    SNAPSHOT,
    ROOM,
}
