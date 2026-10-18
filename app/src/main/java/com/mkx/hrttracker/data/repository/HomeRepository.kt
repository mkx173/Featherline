package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        // placeholder: the E2 skeleton's chart toggle shows the user's choice.
        return combine(
            // Current Home data only: without it the first frame waits for Room.
            homeSnapshotRepository.currentCache.mapNotNull { state -> state?.homeData },
            settingsRepository.homeE2ChartWindowOptionFlow,
            settingsRepository.settingsState,
        ) { homeData, option, settingsState ->
            val stockWarnings = medicineStockRepository.projectAll(
                medicines = homeData.stockMedicines,
                activeGroups = homeData.activeGroups,
                logEntries = homeData.stockFulfillmentEntries,
                now = now.atZone(zoneId).toInstant(),
                zoneId = zoneId,
            ).stockWarningsOnly()
            HomeInputs(
                activeGroups = homeData.activeGroups,
                archivedGroups = homeData.archivedGroups,
                scheduleEntries = homeSnapshotRepository.scheduleEntriesForHome(
                    snapshot = homeData,
                    now = now,
                    zoneId = zoneId,
                ),
                antiandrogenHistoryEntries = homeData.antiandrogenHistoryEntries,
                // settingsState's eager placeholder could disagree with the raw
                // option flow on cold start; copy the raw value in.
                settings = settingsState.copy(homeE2ChartWindowOption = option),
                latestEstradiolEntry = homeData.latestEstradiolEntry,
                stockWarnings = stockWarnings,
                homeAnchor = homeData.homeAnchor,
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
            settingsRepository.homeE2ChartWindowOptionFlow,
            stockAndAnchorInputsFlow,
            nowFlow,
        ) { inputs, option, stockAndAnchorInputs, now ->
            suppressInconsistentHomeEmission("room_inputs") {
                val stockWarnings = medicineStockRepository.projectAll(
                    medicines = stockAndAnchorInputs.trackedMedicines,
                    activeGroups = inputs.activeGroups,
                    logEntries = stockAndAnchorInputs.stockFulfillmentEntries,
                    now = now.atZone(zoneId).toInstant(),
                    zoneId = zoneId,
                ).stockWarningsOnly()
                HomeInputs(
                    activeGroups = inputs.activeGroups,
                    archivedGroups = inputs.archivedGroups,
                    scheduleEntries = inputs.scheduleEntries,
                    antiandrogenHistoryEntries = inputs.antiandrogenHistoryEntries,
                    // The raw option, so the E2 skeleton's chart toggle never
                    // shows settingsState's eager placeholder.
                    settings = inputs.settings.copy(homeE2ChartWindowOption = option),
                    latestEstradiolEntry = inputs.latestEstradiolEntry,
                    stockWarnings = stockWarnings,
                    homeAnchor = stockAndAnchorInputs.homeAnchor,
                    source = HomeInputSource.ROOM,
                    now = now,
                )
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
                        settings = fallbackSettings,
                        latestEstradiolEntry = null,
                        source = HomeInputSource.ROOM,
                        now = nowFlow.value,
                    )
                )
            }
            .flowOn(Dispatchers.IO)
    }

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
        // the day after tomorrow, covering the next medication day’s early hours.
        val manualEndEpochMillis = today.plusDays(2)
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

        return flow {
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
            val basicsFlow = combine(
                activeGroupsFlow,
                archivedGroupsFlow,
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
                settingsRepository.settingsState,
            ) { activeGroups, archivedGroups, scheduleEntries, antiandrogenHistoryEntries, settingsState ->
                HomeStartupInputs(
                    activeGroups = activeGroups,
                    archivedGroups = archivedGroups,
                    scheduleEntries = scheduleEntries,
                    antiandrogenHistoryEntries = antiandrogenHistoryEntries,
                    settings = settingsState,
                    latestEstradiolEntry = null,
                )
            }
            emitAll(
                combine(
                    basicsFlow,
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
                ) { basics, latestEstradiolEntry ->
                    basics.copy(latestEstradiolEntry = latestEstradiolEntry.entry)
                }
            )
        }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                diagnosticsLogger.warning(TAG, "home_startup_inputs_failed", throwable)
                emit(
                    HomeStartupInputs(
                        activeGroups = emptyList(),
                        scheduleEntries = emptyList(),
                        antiandrogenHistoryEntries = emptyList(),
                        settings = settingsRepository.settingsState.value,
                        latestEstradiolEntry = null,
                    )
                )
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * A data change like any other: Home holds its chart, with the updating
     * indicator, until the new window's estimate is derived, and the
     * mutation's rebuild reads the new option. This is the only in-app writer
     * of the option; restore writes it inside its own mutation.
     */
    suspend fun setHomeE2ChartWindowOption(option: HomeE2ChartWindowOption) {
        homeSnapshotRepository.runHomeDataMutation {
            settingsRepository.setHomeE2ChartWindowOption(option)
        }
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
            // invalidation recovers — a fabricated default (an empty list) would
            // reach the Home combine and render a one-frame wrong state before
            // the consistent emission lands. Catch Exception (not
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
    val settings: SettingsState,
    val latestEstradiolEntry: MedicationLogEntry?,
)

/** Home's data apart from the E2 estimate, which E2EstimateRepository derives. */
data class HomeInputs(
    val activeGroups: List<com.mkx.hrttracker.model.medication.MedicationGroup>,
    val archivedGroups: List<com.mkx.hrttracker.model.medication.MedicationGroup> = emptyList(),
    val scheduleEntries: List<MedicationLogEntry>,
    val antiandrogenHistoryEntries: List<MedicationLogEntry>,
    val settings: SettingsState,
    /** The hero's last dose; the schedule window carries any logged after it. */
    val latestEstradiolEntry: MedicationLogEntry?,
    val stockWarnings: List<MedicineStockProjection> = emptyList(),
    val homeAnchor: TrackedDate? = null,
    val source: HomeInputSource,
    val now: LocalDateTime,
)

enum class HomeInputSource {
    SNAPSHOT,
    ROOM,
}
