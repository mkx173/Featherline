package com.mkx.hrttracker.data.repository

import android.util.Log
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.PkTrendResult
import com.mkx.hrttracker.startup.StartupTiming
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
) {
    private val refreshMutex = Mutex()
    private val homeDataMutationMutex = Mutex()
    private val snapshotMutationMutex = Mutex()

    fun observeHomeSnapshot(): Flow<HomeSnapshotRecord?> {
        return combine(
            homeSnapshotStore.observeSnapshot(),
            homeSnapshotGenerationStore.observeGeneration(),
        ) { snapshot, generation ->
            snapshot?.takeIf {
                it.generation >= generation
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
                homeSnapshotGenerationStore.incrementGeneration()
                val result = block()
                clearSnapshotBestEffort()
                refreshHomeSnapshotAsync(force = true)
                result
            }
        }
    }

    fun isSnapshotUsable(
        snapshot: HomeSnapshotRecord,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (snapshot.schemaVersion != HOME_SNAPSHOT_SCHEMA_VERSION) {
            return false
        }
        if (snapshot.zoneId != zoneId.id) {
            return false
        }
        val anchorDate = LocalDate.ofEpochDay(snapshot.anchorDateEpochDay)
        val today = now.toLocalDate()
        if (today.isBefore(anchorDate) || today.isAfter(anchorDate.plusDays(HOME_SNAPSHOT_VALIDITY_DAYS))) {
            return false
        }
        val pkProjection = snapshot.pkProjection ?: return false
        val chartWindow = HomePkChartWindow.forNow(now = now, zoneId = zoneId)
        return pkProjection.windowStartEpochMillis <= chartWindow.windowStartEpochMillis &&
            pkProjection.windowEndEpochMillis >= chartWindow.windowEndEpochMillis
    }

    suspend fun readUsableHomeSnapshot(
        now: LocalDateTime = LocalDateTime.now(),
    ): HomeSnapshotRecord? {
        val snapshot = homeSnapshotStore.readSnapshot() ?: return null
        val generation = homeSnapshotGenerationStore.readGeneration()
        return snapshot.takeIf {
            it.generation >= generation &&
                isSnapshotUsable(
                    snapshot = it,
                    now = now,
                    zoneId = ZoneId.systemDefault(),
                )
        }
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

    fun refreshHomeSnapshotAsync(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
    ) {
        appScope.launch {
            try {
                refreshHomeSnapshotIfNeeded(now = now, force = force)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.w(TAG, "Home snapshot refresh failed.", throwable)
            }
        }
    }

    suspend fun invalidateHomeSnapshot() {
        homeSnapshotGenerationStore.incrementGeneration()
        snapshotMutationMutex.withLock {
            homeSnapshotStore.clearSnapshot()
        }
    }

    suspend fun refreshHomeSnapshotIfNeeded(
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false,
    ) {
        refreshMutex.withLock {
            val refreshGeneration = homeSnapshotGenerationStore.readGeneration()
            val zoneId = ZoneId.systemDefault()
            val cacheWindow = HomePkProjectionWindow.forNow(now = now, zoneId = zoneId)
            val snapshotWindow = HomeSnapshotWindow.forNow(now = now, zoneId = zoneId)
            val existingSnapshot = homeSnapshotStore.readSnapshot()
            if (
                !force &&
                existingSnapshot != null &&
                existingSnapshot.generation >= refreshGeneration &&
                isSnapshotUsable(existingSnapshot, now, zoneId)
            ) {
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
                        homeSnapshotStore.writeSnapshot(snapshotRecord)
                        StartupTiming.mark("home_snapshot_rebuilt")
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
        }.onFailure { throwable ->
            Log.w(TAG, "Home snapshot clear failed.", throwable)
        }
    }
}

internal const val HOME_SNAPSHOT_SCHEMA_VERSION = 3
private const val TAG = "HomeSnapshotRepository"
private const val HOME_SNAPSHOT_SOURCE_VERSION = "home-snapshot-v2"
private const val HOME_PK_PROJECTION_LOOKBACK_DAYS = 180L
private const val HOME_PK_PROJECTION_FUTURE_DAYS = 14L
private const val HOME_SCHEDULE_LOOKAHEAD_DAYS = 90L
private const val HOME_SNAPSHOT_VALIDITY_DAYS = 10L
private const val HOME_SNAPSHOT_PAST_BUFFER_DAYS = 1L
