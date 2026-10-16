package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    @AppScope appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val profileFlow: StateFlow<UserProfile?> =
        databaseHolder.databaseFlow
            .flatMapLatest { database ->
                if (database == null) {
                    flowOf<UserProfile?>(null)
                } else {
                    database.userProfileDao().observeProfile()
                        .map<UserProfileEntity?, UserProfile?> { entity ->
                            // Handle per emission rather than via a terminal `.catch`
                            // inside flatMapLatest: a terminal catch would freeze this
                            // Eagerly, app-scoped flow — and the Settings weight — until
                            // the database (app process) is rebuilt. toUserProfileModel() can't
                            // currently throw, so this is defensive parity with the
                            // medication/medicine flows that share this shape. Suppress
                            // (null -> filterNotNull below) rather than emit a default:
                            // a fabricated default profile would flash for one frame
                            // before the next consistent emission lands.
                            runCatching { entity?.toUserProfileModel() ?: UserProfile() }
                                .getOrElse { error ->
                                    // No suspension point inside the block today, so
                                    // cancellation can't surface here — but rethrow it
                                    // anyway so runCatching never silently swallows
                                    // cancellation if a suspend call is added later.
                                    // Rethrow non-Exception Throwables (Errors) too:
                                    // only genuine recoverable failures may be
                                    // suppressed; an Error must keep failing loud.
                                    if (error is CancellationException) throw error
                                    if (error !is Exception) throw error
                                    null
                                }
                        }
                        .filterNotNull()
                        // Per-emission runCatching above recovers from a transform
                        // failure; this terminal catch separately guards the Room flow
                        // itself failing (DB corruption / I/O error). Without it a
                        // recoverable upstream error would reach the Eagerly, app-scoped
                        // stateIn collector and crash the process — appScope is a
                        // SupervisorJob with no CoroutineExceptionHandler. Rethrow
                        // CancellationException and non-Exception Throwables (Errors) so
                        // only genuine recoverable failures degrade to the default.
                        .catch { error ->
                            if (error is CancellationException) throw error
                            if (error !is Exception) throw error
                            emit(UserProfile())
                        }
                }
            }
            // Seed the flow from the cached Home data so screens that bind the
            // profile (Settings) render the real weight on first frame instead of
            // flashing the empty default while Room cold-init completes. Failures
            // are swallowed — the Room flow follows immediately and is authoritative.
            .onStart {
                runCatching {
                    homeSnapshotRepository.currentState()?.homeData?.userProfile
                }.getOrElse { error ->
                    // currentState() suspends; don't let runCatching swallow
                    // cancellation of the collecting scope. Any other failure is
                    // non-fatal here — the authoritative Room flow follows immediately.
                    if (error is CancellationException) throw error
                    null
                }?.let { emit(it) }
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    fun observeProfile(): Flow<UserProfile?> = profileFlow

    suspend fun getCurrentProfile(): UserProfile {
        return databaseHolder.get().userProfileDao()
            .getProfile()
            ?.toUserProfileModel()
            ?: UserProfile()
    }

    suspend fun setWeight(
        originalValue: Double,
        originalUnit: WeightUnit,
        now: Instant = Instant.now(),
    ) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.get().userProfileDao().upsertProfile(
                UserProfileEntity(
                    weightKg = originalUnit.toKg(originalValue),
                    weightOriginalValue = originalValue,
                    weightOriginalUnit = originalUnit.name,
                    updatedAtEpochMillis = now.toEpochMilli(),
                    weightMeasuredAtEpochMillis = null,
                )
            )
        }
    }

    // Keeps the unit, so a Health Connect reading adopted later still shows in
    // the unit the user picked.
    suspend fun clearWeight(now: Instant = Instant.now()) {
        homeSnapshotRepository.runHomeDataMutation {
            val dao = databaseHolder.get().userProfileDao()
            dao.upsertProfile(
                UserProfileEntity(
                    weightKg = null,
                    weightOriginalValue = null,
                    weightOriginalUnit = dao.getProfile()?.weightOriginalUnit,
                    updatedAtEpochMillis = now.toEpochMilli(),
                    weightMeasuredAtEpochMillis = null,
                )
            )
        }
    }

    /**
     * Makes a Health Connect reading the weight (with sync on, HC is the source).
     * The value is rounded to 0.1 in the user's last unit and kg is derived from
     * that, which keeps the backup validator's kg/unit consistency check true.
     * Returns false without writing when the reading is the one already stored
     * (same time in millis, same rounded value).
     */
    suspend fun adoptHcWeight(
        weightKg: Double,
        measuredAt: Instant,
        now: Instant = Instant.now(),
    ): Boolean = homeSnapshotRepository.runHomeDataMutation {
        val dao = databaseHolder.get().userProfileDao()
        val current = dao.getProfile()
        val unit = WeightUnit.fromStorageValue(current?.weightOriginalUnit)
        val originalValue = roundWeightToTenth(weightKg / unit.kgPerUnit)
        // Millis on both sides: the column stores millis, and a reading with
        // sub-millisecond precision must not look changed on every sync.
        if (current?.weightMeasuredAtEpochMillis == measuredAt.toEpochMilli() &&
            current.weightOriginalValue == originalValue
        ) {
            return@runHomeDataMutation false
        }
        dao.upsertProfile(
            UserProfileEntity(
                weightKg = unit.toKg(originalValue),
                weightOriginalValue = originalValue,
                weightOriginalUnit = unit.name,
                updatedAtEpochMillis = now.toEpochMilli(),
                weightMeasuredAtEpochMillis = measuredAt.toEpochMilli(),
            )
        )
        true
    }
}

internal fun roundWeightToTenth(value: Double): Double = Math.round(value * 10.0) / 10.0
