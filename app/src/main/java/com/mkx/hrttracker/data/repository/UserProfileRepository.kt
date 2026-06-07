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
                            // the database (app process) is rebuilt. toModel() can't
                            // currently throw, so this is defensive parity with the
                            // medication/medicine flows that share this shape.
                            runCatching { entity?.toModel() ?: UserProfile() }
                                .getOrElse { error ->
                                    // No suspension point inside the block today, so
                                    // cancellation can't surface here — but rethrow it
                                    // anyway so runCatching never silently swallows
                                    // cancellation if a suspend call is added later.
                                    if (error is CancellationException) throw error
                                    UserProfile()
                                }
                        }
                }
            }
            // Seed the flow from the home snapshot cache so screens that bind the
            // profile (Settings) render the real weight on first frame instead of
            // flashing the empty default while Room cold-init completes. Failures
            // are swallowed — the Room flow follows immediately and is authoritative.
            .onStart {
                runCatching {
                    homeSnapshotRepository.readUsableHomeSnapshot()?.userProfile
                }.getOrElse { error ->
                    // readUsableHomeSnapshot() suspends; don't let runCatching swallow
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
            ?.toModel()
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
                    updatedAtEpochMillis = now.toEpochMilli()
                )
            )
        }
    }

    suspend fun clearWeight(now: Instant = Instant.now()) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.get().userProfileDao().upsertProfile(
                UserProfileEntity(
                    weightKg = null,
                    weightOriginalValue = null,
                    weightOriginalUnit = null,
                    updatedAtEpochMillis = now.toEpochMilli()
                )
            )
        }
    }

    private fun UserProfileEntity.toModel(): UserProfile {
        return UserProfile(
            weightKg = weightKg,
            weightOriginalValue = weightOriginalValue,
            weightOriginalUnit = WeightUnit.fromStorageValue(weightOriginalUnit),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
        )
    }
}
