package com.mkx.hrttracker.startup

import android.util.Log
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupPreloader @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val databaseHolder: DatabaseHolder,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val userProfileRepository: UserProfileRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val started = AtomicBoolean(false)

    fun startAfterFirstHomeFrame() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        appScope.launch(Dispatchers.IO) {
            runCatching {
                databaseHolder.get().openHelper.writableDatabase
            }.onFailure { throwable ->
                Log.w(TAG, "Database warm-up failed.", throwable)
            }

            runCatching {
                medicationGroupRepository.getGroups()
                medicationLogRepository.getEntries()
                userProfileRepository.getCurrentProfile()
                settingsRepository.getCurrentSettings()
            }.onFailure { throwable ->
                Log.w(TAG, "Repository warm-up failed.", throwable)
            }
        }
    }

    private companion object {
        const val TAG = "StartupPreloader"
    }
}
