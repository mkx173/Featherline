package com.mkx.hrttracker.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.AlarmVibrationPatternOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.ACTION_MEDICATION_REMINDER_DISMISS
import com.mkx.hrttracker.reminder.ACTION_MEDICATION_REMINDER_LOG_NOW
import com.mkx.hrttracker.reminder.ACTION_MEDICATION_REMINDER_REMIND_LATER
import com.mkx.hrttracker.reminder.EXTRA_NOTIFICATION_TAG
import com.mkx.hrttracker.reminder.EXTRA_REMINDER_SLOTS
import com.mkx.hrttracker.reminder.MedicationReminderBundle
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.buildReminderActionIntent
import com.mkx.hrttracker.reminder.medicationReminderSlotFromStorageValue
import com.mkx.hrttracker.reminder.remainingMedicationReminderBundle
import com.mkx.hrttracker.reminder.toStorageValue
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale
import javax.inject.Inject

/**
 * The full-screen dose alarm. Launched by the system from a dose reminder's
 * full-screen intent when the device is locked or its screen is off; it turns
 * the screen on, shows over the lock screen, and rings until the user acts.
 *
 * Its actions send the same broadcasts as the notification's buttons
 * ([buildReminderActionIntent]), so logging, snoozing and stopping run the
 * one handler path, logging exactly the medications shown here.
 *
 * Like the notification on the lock screen, it shows what a reminder shows
 * there and no more: medication details follow "Hide medication details" (and
 * stay hidden over the lock screen while the app lock is on), and opening the
 * app goes through its own lock.
 */
@AndroidEntryPoint
class DoseAlarmActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var medicationGroupRepository: MedicationGroupRepository

    @Inject
    lateinit var medicationLogRepository: MedicationLogRepository

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    private val ringer by lazy { DoseAlarmRinger(this) }

    // Replaced by onNewIntent when a repeat relaunches the alarm while it shows.
    private var request by mutableStateOf<DoseAlarmRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        request = DoseAlarmRequest.from(intent)
        if (request == null) {
            diagnosticsLogger.info(TAG, "dose_alarm_finish reason=invalid_intent")
            finish()
            return
        }

        setContent {
            val currentRequest = request ?: return@setContent
            // Settings first: theme, language, details visibility and ringing
            // all come from them, so nothing is drawn on a placeholder.
            val settings by produceState<SettingsState?>(initialValue = null) {
                value = try {
                    settingsRepository.getCurrentSettings()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    diagnosticsLogger.warning(TAG, "dose_alarm_settings_failed", error)
                    finish()
                    null
                }
            }
            val loadedSettings = settings ?: return@setContent

            val content by produceState<DoseAlarmContent?>(initialValue = null, currentRequest) {
                value = null
                value = if (currentRequest.preview) {
                    DoseAlarmContent.Preview
                } else {
                    loadPendingBundle(currentRequest)?.let(DoseAlarmContent::Pending)
                        ?: run {
                            diagnosticsLogger.info(TAG, "dose_alarm_finish reason=nothing_pending")
                            finish()
                            return@produceState
                        }
                }
                // Logged from the notification or the app while this shows:
                // close rather than ask for a dose that is done.
                while (!currentRequest.preview) {
                    delay(PENDING_RECHECK_MS)
                    val stillPending = loadPendingBundle(currentRequest)
                    if (stillPending == null) {
                        diagnosticsLogger.info(TAG, "dose_alarm_finish reason=resolved_elsewhere")
                        finish()
                        return@produceState
                    }
                    value = DoseAlarmContent.Pending(stillPending)
                }
            }

            val persistent = loadedSettings.persistentReminders
            // Ring only once there is a pending dose to show: a dose logged
            // just before the alarm opened closes it without a sound.
            val hasContent = content != null
            LaunchedEffect(currentRequest, hasContent) {
                if (!hasContent) return@LaunchedEffect
                ringer.start(
                    sound = persistent.alarmSoundEnabled,
                    vibration = persistent.alarmVibrationEnabled,
                    soundUri = persistent.customSoundUri?.toUri(),
                    continuousVibration =
                        persistent.alarmVibrationPattern == AlarmVibrationPatternOption.CONTINUOUS,
                )
                // Left unanswered, the alarm goes away by itself and does
                // nothing else: the dose stays unlogged, its notification stays
                // up and its repeats carry on. Kept up, the screen would wait
                // for a stray touch, from a pocket or a swipe to unlock.
                delay(ALARM_SCREEN_TIMEOUT_MS)
                diagnosticsLogger.info(TAG, "dose_alarm_finish reason=unanswered_timeout")
                ringer.stop()
                finish()
            }
            // With the app lock on, medication names stay off the lock screen,
            // as a notification's do under "hide sensitive content".
            val hideMedicationDetails = loadedSettings.hideMedicationDetails ||
                    (loadedSettings.screenLockProtectionEnabled && isKeyguardLocked())

            LocalizedContent(languageTag = loadedSettings.appLanguageOption.languageTag) {
                HrtTrackerTheme(
                    darkTheme = loadedSettings.darkModeOption.resolveDarkTheme(isSystemInDarkTheme()),
                    dynamicColor = loadedSettings.adaptiveColorEnabled,
                    amoled = loadedSettings.pureBlackEnabled,
                ) {
                    LaunchedEffect(loadedSettings.hideScreenContentEnabled) {
                        applyHideScreenContent(loadedSettings.hideScreenContentEnabled)
                    }
                    content?.let { loadedContent ->
                        DoseAlarmScreen(
                            content = loadedContent,
                            hideMedicationDetails = hideMedicationDetails,
                            canSnooze = currentRequest.canSnooze,
                            snoozeMinutes = persistent.snoozeDuration.minutes,
                            canStopRepeats = persistent.repeatEnabled,
                            onLog = { bundle -> act(ACTION_MEDICATION_REMINDER_LOG_NOW, bundle) },
                            onSnooze = { bundle -> act(ACTION_MEDICATION_REMINDER_REMIND_LATER, bundle) },
                            onStop = { bundle -> act(ACTION_MEDICATION_REMINDER_DISMISS, bundle) },
                            onSilence = ringer::stop,
                            onOpenApp = ::openApp,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DoseAlarmRequest.from(intent)?.let { newRequest -> request = newRequest }
    }

    override fun onStop() {
        super.onStop()
        // Screen off or left: an alarm nobody can see should not keep ringing.
        // The repeat chain, not this screen, brings the reminder back.
        ringer.stop()
    }

    override fun onDestroy() {
        ringer.stop()
        super.onDestroy()
    }

    /**
     * Sends [action] for what is shown, then closes. A null [bundle] is the
     * settings preview, which only closes.
     */
    private fun act(action: String, bundle: MedicationReminderBundle?) {
        // One action per alarm: nothing more is sent once it is closing.
        if (isFinishing) return
        ringer.stop()
        val notificationTag = request?.notificationTag
        diagnosticsLogger.info(
            TAG,
            "dose_alarm_action action=$action items=${bundle?.items?.size ?: 0} " +
                    "preview=${bundle == null}"
        )
        if (bundle != null && notificationTag != null) {
            sendBroadcast(buildReminderActionIntent(this, action, bundle, notificationTag))
        }
        finish()
    }

    private fun openApp() {
        ringer.stop()
        val launch = {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isKeyguardLocked == true) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = launch()
                },
            )
        } else {
            launch()
        }
    }

    private fun isKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private suspend fun loadPendingBundle(request: DoseAlarmRequest): MedicationReminderBundle? {
        return try {
            remainingMedicationReminderBundle(
                slots = request.slots,
                groups = medicationGroupRepository.getGroups(),
                entries = medicationLogRepository.getScheduledGroupEntriesSince(
                    request.slots.minOf(MedicationReminderSlot::scheduledAt)
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            diagnosticsLogger.warning(TAG, "dose_alarm_load_failed", error)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applyHideScreenContent(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        private const val TAG = "DoseAlarmActivity"
        private const val PENDING_RECHECK_MS = 10_000L

        // Matches the ringer's cap: the alarm stays only while it rings.
        private const val ALARM_SCREEN_TIMEOUT_MS = 60_000L
        private const val EXTRA_CAN_SNOOZE = "doseAlarmCanSnooze"
        private const val EXTRA_PREVIEW = "doseAlarmPreview"
        private const val ALARM_URI_PREFIX = "hrttracker://medication-reminder-alarm"

        /** The full-screen intent for the reminder [bundle] posted under [notificationTag]. */
        fun intent(
            context: Context,
            bundle: MedicationReminderBundle,
            notificationTag: String,
            canSnooze: Boolean,
        ): Intent {
            return Intent(context, DoseAlarmActivity::class.java).apply {
                // Distinct per reminder, so each one's PendingIntent stays its own.
                data = "$ALARM_URI_PREFIX/$notificationTag".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putStringArrayListExtra(
                    EXTRA_REMINDER_SLOTS,
                    ArrayList(bundle.slots.map(MedicationReminderSlot::toStorageValue)),
                )
                putExtra(EXTRA_NOTIFICATION_TAG, notificationTag)
                putExtra(EXTRA_CAN_SNOOZE, canSnooze)
            }
        }

        /** Shows the alarm with sample doses, from settings, so the user can try it. */
        fun previewIntent(context: Context): Intent {
            return Intent(context, DoseAlarmActivity::class.java).apply {
                data = "$ALARM_URI_PREFIX/preview".toUri()
                putExtra(EXTRA_PREVIEW, true)
                putExtra(EXTRA_CAN_SNOOZE, true)
            }
        }

        internal fun requestFrom(intent: Intent?): DoseAlarmRequest? {
            intent ?: return null
            if (intent.getBooleanExtra(EXTRA_PREVIEW, false)) {
                return DoseAlarmRequest(
                    slots = emptyList(),
                    notificationTag = null,
                    canSnooze = true,
                    preview = true,
                )
            }
            val slots = intent.getStringArrayListExtra(EXTRA_REMINDER_SLOTS)
                .orEmpty()
                .mapNotNull(::medicationReminderSlotFromStorageValue)
            val notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
            if (slots.isEmpty() || notificationTag == null) {
                return null
            }
            return DoseAlarmRequest(
                slots = slots,
                notificationTag = notificationTag,
                canSnooze = intent.getBooleanExtra(EXTRA_CAN_SNOOZE, true),
                preview = false,
            )
        }
    }
}

/** What one launch of the alarm asks it to show. */
internal data class DoseAlarmRequest(
    val slots: List<MedicationReminderSlot>,
    val notificationTag: String?,
    val canSnooze: Boolean,
    val preview: Boolean,
) {
    companion object {
        fun from(intent: Intent?): DoseAlarmRequest? = DoseAlarmActivity.requestFrom(intent)
    }
}

internal sealed interface DoseAlarmContent {
    data class Pending(val bundle: MedicationReminderBundle) : DoseAlarmContent
    data object Preview : DoseAlarmContent
}

/**
 * Serves resources in the chosen app language, as MainActivity does, so the
 * alarm reads in the same language as the app even below API 33.
 */
@androidx.compose.runtime.Composable
private fun LocalizedContent(
    languageTag: String,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localizedContext = remember(baseContext, baseConfiguration, languageTag) {
        val configuration = Configuration(baseConfiguration)
            .apply { setLocale(Locale.forLanguageTag(languageTag)) }
        val localizedResources = baseContext.createConfigurationContext(configuration).resources
        object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = localizedResources
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content,
    )
}
