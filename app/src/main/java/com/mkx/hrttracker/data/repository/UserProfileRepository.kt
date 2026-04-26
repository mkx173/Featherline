package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
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
                            entity?.toModel() ?: UserProfile()
                        }
                        .catch { emit(UserProfile()) }
                }
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
        databaseHolder.get().userProfileDao().upsertProfile(
            UserProfileEntity(
                weightKg = originalUnit.toKg(originalValue),
                weightOriginalValue = originalValue,
                weightOriginalUnit = originalUnit.name,
                updatedAtEpochMillis = now.toEpochMilli()
            )
        )
    }

    suspend fun clearWeight(now: Instant = Instant.now()) {
        databaseHolder.get().userProfileDao().upsertProfile(
            UserProfileEntity(
                weightKg = null,
                weightOriginalValue = null,
                weightOriginalUnit = null,
                updatedAtEpochMillis = now.toEpochMilli()
            )
        )
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
