package com.mkx.hrttracker

import android.app.Application
import android.app.UiModeManager
import androidx.appcompat.app.AppCompatDelegate
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.util.ToastManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HrtTrackerApplication : Application() {
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var reminderNotificationManager: ReminderNotificationManager

    @Inject
    lateinit var medicationReminderScheduler: MedicationReminderScheduler

    override fun onCreate() {
        super.onCreate()

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        applicationScope.launch {
            applyDarkMode(settingsRepository.getCurrentSettings().darkModeOption)
            settingsRepository.settingsState.collect { settings ->
                applyDarkMode(settings.darkModeOption)
            }
        }
        ToastManager.init(this)
        reminderNotificationManager.createNotificationChannel()
        appScope.launch {
            medicationReminderScheduler.rescheduleAll()
        }
    }

    private fun applyDarkMode(option: DarkModeOption) {
        AppCompatDelegate.setDefaultNightMode(option.appCompatNightMode)
        getSystemService(UiModeManager::class.java).setApplicationNightMode(option.applicationNightMode)
    }
}
