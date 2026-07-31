package com.mkx.hrttracker

import android.annotation.SuppressLint
import android.app.Application
import android.app.UiModeManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.healthconnect.HealthConnectSyncCoordinator
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.TimeZoneChangeNoticeController
import com.mkx.hrttracker.util.ToastManager
import com.mkx.hrttracker.widget.HomeWidgetManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    @Inject
    lateinit var timeZoneChangeNoticeController: TimeZoneChangeNoticeController

    @Inject
    lateinit var homeWidgetManager: HomeWidgetManager

    @Inject
    lateinit var anchorWidgetManager: com.mkx.hrttracker.widget.AnchorWidgetManager

    @Inject
    lateinit var appTimeSource: AppTimeSource

    @Inject
    lateinit var healthConnectSyncCoordinator: HealthConnectSyncCoordinator

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate() {
        ComposeMaterial3Flags.isCheckboxStylingFixEnabled = true

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
        // Re-register the reminder channel whenever the app locale changes so
        // its name/description in system Settings → Apps → Notifications
        // reflect the new language. Calling createNotificationChannel with the
        // same channel ID updates the (mutable) name + description fields and
        // leaves everything else untouched.
        applicationScope.launch {
            var lastSeen: AppLanguageOption? = null
            settingsRepository.settingsState
                .map { it.appLanguageOption }
                .distinctUntilChanged()
                .collect { option ->
                    // Skip the initial emission — the channel is already
                    // created with the current locale right after this block
                    // starts. We only need to react to actual changes.
                    if (lastSeen != null && lastSeen != option) {
                        diagnosticsLogger.info(
                            TAG,
                            "application_notification_channel_refresh_for_locale option=$option"
                        )
                        // Pass the new language tag so the channel's
                        // name/description resolve via a config-overridden
                        // context — the ApplicationContext's Resources may
                        // still be on the old locale at this point.
                        reminderNotificationManager.createNotificationChannel(
                            languageTag = option.languageTag,
                        )
                    }
                    lastSeen = option
                }
        }
        ToastManager.init(this)
        diagnosticsLogger.info(TAG, "application_toast_manager_initialized")
        reminderNotificationManager.createNotificationChannel()
        diagnosticsLogger.info(TAG, "application_notification_channel_requested")
        timeZoneChangeNoticeController.attachToProcessLifecycle()
        diagnosticsLogger.info(TAG, "application_timezone_notice_controller_attached")
        // Re-read the wall clock on every foreground so the home/plan/history
        // screens reflect a date/time change that happened while backgrounded.
        // The minute-ticker StateFlow otherwise stays stale until its next
        // monotonic tick (up to a minute, longer while the process is frozen).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appTimeSource.refresh()
                healthConnectSyncCoordinator.onForeground()
            }
        })
        diagnosticsLogger.info(TAG, "application_app_time_source_refresh_attached")
        healthConnectSyncCoordinator.start()
        diagnosticsLogger.info(TAG, "application_health_connect_sync_started")
        homeWidgetManager.start()
        diagnosticsLogger.info(TAG, "application_home_widget_manager_started")
        anchorWidgetManager.start()
        diagnosticsLogger.info(TAG, "application_anchor_widget_manager_started")
        diagnosticsLogger.info(TAG, "application_on_create_complete")
    }

    private fun applyDarkMode(option: DarkModeOption) {
        val appCompatNightMode = when (option) {
            DarkModeOption.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            DarkModeOption.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DarkModeOption.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        val applicationNightMode = when (option) {
            DarkModeOption.FOLLOW_SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            DarkModeOption.LIGHT -> UiModeManager.MODE_NIGHT_NO
            DarkModeOption.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(appCompatNightMode)
        applyApplicationNightModeIfAvailable(
            uiModeManager = getSystemService(UiModeManager::class.java),
            applicationNightMode = applicationNightMode,
        )
    }

    private companion object {
        const val TAG = "HrtTrackerApplication"
    }
}

internal fun applyApplicationNightModeIfAvailable(
    uiModeManager: UiModeManager,
    applicationNightMode: Int,
    sdkInt: Int = Build.VERSION.SDK_INT,
) {
    if (sdkInt < Build.VERSION_CODES.S) return
    applyApplicationNightModeOnSOrAbove(uiModeManager, applicationNightMode)
}

@SuppressLint("NewApi")
private fun applyApplicationNightModeOnSOrAbove(
    uiModeManager: UiModeManager,
    applicationNightMode: Int,
) {
    uiModeManager.setApplicationNightMode(applicationNightMode)
}
