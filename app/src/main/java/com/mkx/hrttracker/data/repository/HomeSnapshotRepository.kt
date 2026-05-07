package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.PkTrendResult
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
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
    private val homeDataMutationMutex = Mutex()
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
        return homeDataMutationMutex.withLock {
            refreshMutex.withLock {
                diagnosticsLogger.info(TAG, "home_data_mutation_start")
                val generation = homeSnapshotGenerationStore.incrementGeneration()
                diagnosticsLogger.info(
                    TAG,
                    "home_data_mutation_generation_incremented generation=$generation"
                )
                val result = block()
                diagnosticsLogger.info(TAG, "home_data_mutation_committed generation=$generation")
                clearSnapshotBestEffort()
                refreshHomeSnapshotAsync(force = true)
                diagnosticsLogger.info(
                    TAG,
                    "home_data_mutation_snapshot_refresh_requested generation=$generation"
                )
                result
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

    fun trendResultFromProjection(
        projectionRecord: HomePkProjectionRecord?,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PkTrendResult? {
        projectionRecord ?: return null
        return runCatching {
            HomePkProjectionJsonCodec.decode(projectionRecord.payloadJson)
                ?.toProjection(
                    generatedAtEpochMillis = projectionRecord.generatedAtEpochMillis,
                    windowStartEpochMillis = projectionRecord.windowStartEpochMillis,
                    windowEndEpochMillis = projectionRecord.windowEndEpochMillis,
                )
                ?.toMainEstradiolTrend(now = now, zoneId = zoneId)
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
        refreshMutex.withLock {
            val refreshGeneration = homeSnapshotGenerationStore.readGeneration()
            val zoneId = ZoneId.systemDefault()
            val cacheWindow = HomePkProjectionWindow.forNow(now = now, zoneId = zoneId)
            val snapshotWindow = HomeSnapshotWindow.forNow(now = now, zoneId = zoneId)
            val existingSnapshot = homeSnapshotStore.readSnapshot()
            diagnosticsLogger.info(
                TAG,
                "home_snapshot_refresh_verify_existing force=$force " +
                    "generation=$refreshGeneration " +
                    "existing=${existingSnapshot?.diagnosticSummary() ?: "none"}"
            )
            if (
                !force &&
                existingSnapshot != null &&
                existingSnapshot.generation >= refreshGeneration &&
                isSnapshotUsable(existingSnapshot, now, zoneId)
            ) {
                diagnosticsLogger.info(
                    TAG,
                    "home_snapshot_refresh_skipped reason=existing_usable " +
                        "${existingSnapshot.diagnosticSummary()} now=$now"
                )
                return@withLock
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
            val fingerprint = sourceFingerprint(
                zoneId = zoneId,
                anchorDate = now.toLocalDate(),
                inputs = inputs,
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
                    "fingerprint=${fingerprint.shortDiagnosticFingerprint()} " +
                    "windowStart=${projection.windowStart} windowEnd=${projection.windowEnd}"
            )
            val pkProjectionRecord = HomePkProjectionRecord(
                generatedAtEpochMillis = projection.generatedAt.toEpochMilli(),
                windowStartEpochMillis = projection.windowStart.toEpochMilli(),
                windowEndEpochMillis = projection.windowEnd.toEpochMilli(),
                sourceFingerprint = fingerprint,
                payloadJson = HomePkProjectionJsonCodec.encode(projection),
                latestEstradiolEntry = inputs.latestEstradiolEntry,
            )
            val snapshotRecord = HomeSnapshotRecord(
                schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
                generation = refreshGeneration,
                generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
                anchorDateEpochDay = now.toLocalDate().toEpochDay(),
                zoneId = zoneId.id,
                sourceFingerprint = fingerprint,
                pkProjection = pkProjectionRecord,
                activeGroups = inputs.activeGroups,
                scheduleEntries = inputs.scheduleEntries,
                antiandrogenHistoryEntries = inputs.antiandrogenHistoryEntries,
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
    }

    private fun sourceFingerprint(
        zoneId: ZoneId,
        anchorDate: LocalDate,
        inputs: HomeSnapshotBuildInputs,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateUtf8(HOME_SNAPSHOT_SOURCE_VERSION)
        digest.updateUtf8(zoneId.id)
        digest.updateUtf8(anchorDate.toString())
        digest.updateUtf8(inputs.profile.toString())
        inputs.activeGroups
            .sortedBy(MedicationGroup::uuid)
            .forEach { group -> digest.updateUtf8(group.toString()) }
        inputs.scheduleEntries
            .sortedWith(compareBy<MedicationLogEntry> { entry -> entry.appliedAt }.thenBy { entry -> entry.uuid })
            .forEach { entry -> digest.updateUtf8(entry.toString()) }
        inputs.antiandrogenHistoryEntries
            .sortedWith(compareBy<MedicationLogEntry> { entry -> entry.appliedAt }.thenBy { entry -> entry.uuid })
            .forEach { entry -> digest.updateUtf8(entry.toString()) }
        inputs.pkEntries
            .sortedWith(compareBy<MedicationLogEntry> { entry -> entry.appliedAt }.thenBy { entry -> entry.uuid })
            .forEach { entry -> digest.updateUtf8(entry.toString()) }
        digest.updateUtf8(inputs.latestEstradiolEntry?.toString() ?: "null")
        return digest.digest().joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(radix = 16).padStart(2, '0')
        }
    }

    private fun MessageDigest.updateUtf8(value: String) {
        update(value.toByteArray(Charsets.UTF_8))
        update(0.toByte())
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
        "fingerprint=${sourceFingerprint.shortDiagnosticFingerprint()} " +
        "groups=${activeGroups.size} " +
        "scheduleEntries=${scheduleEntries.size} " +
        "antiandrogenEntries=${antiandrogenHistoryEntries.size} " +
        "hasPkProjection=${pkProjection != null}"
}

private fun String.shortDiagnosticFingerprint(): String = take(12)

internal const val HOME_SNAPSHOT_SCHEMA_VERSION = 3
private const val TAG = "HomeSnapshotRepository"
private const val HOME_SNAPSHOT_SOURCE_VERSION = "home-snapshot-v2"
private const val HOME_PK_PROJECTION_LOOKBACK_DAYS = 180L
private const val HOME_PK_PROJECTION_FUTURE_DAYS = 14L
private const val HOME_SCHEDULE_LOOKAHEAD_DAYS = 90L
private const val HOME_SNAPSHOT_VALIDITY_DAYS = 10L
private const val HOME_SNAPSHOT_PAST_BUFFER_DAYS = 1L
