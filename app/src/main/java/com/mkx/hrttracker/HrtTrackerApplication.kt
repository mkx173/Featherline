package com.mkx.hrttracker

import android.app.Application
import android.app.UiModeManager
import androidx.appcompat.app.AppCompatDelegate
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.util.AppDiagnosticsLogger
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
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var reminderNotificationManager: ReminderNotificationManager

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    override fun onCreate() {
        super.onCreate()
        diagnosticsLogger.info(TAG, "application_on_create_start")

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        applicationScope.launch {
            diagnosticsLogger.info(TAG, "application_settings_initial_load_start")
            applyDarkMode(settingsRepository.getCurrentSettings().darkModeOption)
            diagnosticsLogger.info(TAG, "application_settings_initial_load_complete")
            settingsRepository.settingsState.collect { settings ->
                diagnosticsLogger.info(
                    TAG,
                    "application_dark_mode_apply option=${settings.darkModeOption}"
                )
                applyDarkMode(settings.darkModeOption)
            }
        }
        ToastManager.init(this)
        diagnosticsLogger.info(TAG, "application_toast_manager_initialized")
        reminderNotificationManager.createNotificationChannel()
        diagnosticsLogger.info(TAG, "application_notification_channel_requested")
        diagnosticsLogger.info(TAG, "application_on_create_complete")
    }

    private fun applyDarkMode(option: DarkModeOption) {
        AppCompatDelegate.setDefaultNightMode(option.appCompatNightMode)
        getSystemService(UiModeManager::class.java).setApplicationNightMode(option.applicationNightMode)
    }

    private companion object {
        const val TAG = "HrtTrackerApplication"
    }
}
