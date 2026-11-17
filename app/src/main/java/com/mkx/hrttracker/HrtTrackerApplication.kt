package com.mkx.hrttracker

import android.annotation.SuppressLint
import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.TimeZoneChangeNoticeController
import com.mkx.hrttracker.util.ToastManager
import com.mkx.hrttracker.widget.HomeWidgetManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import java.util.Locale
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

    // Lazy: built on the first language change, not at every start.
    @Inject
    lateinit var medicationReminderScheduler: dagger.Lazy<MedicationReminderScheduler>

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate() {
        ComposeMaterial3Flags.isCheckboxStylingFixEnabled = true
        // material3 1.5 raises expressive two-line list items to 72dp and three-line to 88dp.
        // Keep the 1.4 behaviour: a 56dp floor, with rows hugging their text.
        ComposeMaterial3Flags.isExpressiveListItemHeightBasedOnTextLinesFixEnabled = false

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
        applicationScope.launch {
            followAppLanguage(
                languages = settingsRepository.settingsState.map { it.appLanguageOption },
                // Only from API 33 can the language change while the app is dead.
                lastLanguageTag = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        settingsRepository.lastRunAppLanguageTag()
                    } else {
                        null
                    }
                },
                reminderNotificationManager = reminderNotificationManager,
                medicationReminderScheduler = medicationReminderScheduler::get,
                diagnosticsLogger = diagnosticsLogger,
            )
        }
        ToastManager.init(this)
        diagnosticsLogger.info(TAG, "application_toast_manager_initialized")
        applicationScope.launch {
            // After a settings read: below API 33 the app language may be known only from it.
            settingsRepository.getCurrentSettings()
            reminderNotificationManager.createNotificationChannel()
            diagnosticsLogger.info(TAG, "application_notification_channel_requested")
        }
        timeZoneChangeNoticeController.attachToProcessLifecycle()
        diagnosticsLogger.info(TAG, "application_timezone_notice_controller_attached")
        // Re-read the wall clock on every foreground so the home/plan/history
        // screens reflect a date/time change that happened while backgrounded.
        // The minute-ticker StateFlow otherwise stays stale until its next
        // monotonic tick (up to a minute, longer while the process is frozen).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appTimeSource.refresh()
            }
        })
        diagnosticsLogger.info(TAG, "application_app_time_source_refresh_attached")
        homeWidgetManager.start()
        diagnosticsLogger.info(TAG, "application_home_widget_manager_started")
        anchorWidgetManager.start()
        diagnosticsLogger.info(TAG, "application_anchor_widget_manager_started")
        diagnosticsLogger.info(TAG, "application_on_create_complete")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        followAndroidAppLanguageSetting(settingsRepository)
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

/**
 * From API 33 Android's per-app language setting changes the app language with neither the
 * in-app picker nor a new activity (`MainActivity` handles `locale` itself): it arrives only as
 * a configuration change, here read into the app language, so the screens switch and
 * [followAppLanguage] sees it, with or without an activity. Below API 33 the language changes
 * only through the picker, and AppCompat knows it only once an activity has attached, so a
 * configuration change there is left alone.
 */
internal fun followAndroidAppLanguageSetting(
    settingsRepository: SettingsRepository,
    sdkInt: Int = Build.VERSION.SDK_INT,
) {
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) settingsRepository.refreshAppLanguageOption()
}

/**
 * On each change of the app language while the app runs (the in-app picker, or Android's per-app
 * language setting through [followAndroidAppLanguageSetting]): re-registers the reminder channel
 * so its name and description in Android's settings follow (the same channel ID updates only
 * those fields), and reposts the reminders on screen in it, silently. The language the app starts
 * in counts as a change only when it differs from [lastLanguageTag], the one stored by the last
 * run (null skips the check): a change made in Android's setting while the app was dead.
 * Otherwise it is skipped: the channel is created in it right after, and the reminders on screen
 * already show it.
 */
internal suspend fun followAppLanguage(
    languages: Flow<AppLanguageOption>,
    lastLanguageTag: suspend () -> String?,
    reminderNotificationManager: ReminderNotificationManager,
    medicationReminderScheduler: () -> MedicationReminderScheduler,
    diagnosticsLogger: AppDiagnosticsLogger,
) {
    // ponytail: the stored tag only changes when the app sees a language, so a run that ends on
    // "System default" after a choice reposts silently at every start until a language is seen.
    val lastLanguage = lastLanguageTag()
        ?.let { tags -> AppLanguageOption.fromLocale(Locale.forLanguageTag(tags.substringBefore(','))) }
    languages.distinctUntilChanged().withIndex().collect { (index, option) ->
        if (index == 0 && option == (lastLanguage ?: option)) return@collect
        diagnosticsLogger.info(
            "HrtTrackerApplication",
            "application_notification_channel_refresh_for_locale option=$option"
        )
        // Pass the new language tag so the channel's name/description resolve via a
        // config-overridden context — the ApplicationContext's Resources may still be on the
        // old locale at this point.
        reminderNotificationManager.createNotificationChannel(languageTag = option.languageTag)
        try {
            medicationReminderScheduler().refreshDoseReminderNotifications()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            diagnosticsLogger.warning("HrtTrackerApplication", "application_reminder_repost_failed", error)
        }
    }
}
