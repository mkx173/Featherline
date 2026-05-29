package com.mkx.hrttracker

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.reminder.ReminderCapabilityReconciler
import com.mkx.hrttracker.startup.StartupPreloader
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.ui.HrtTrackerApp
import com.mkx.hrttracker.ui.main.DoseRowHighlightKey
import com.mkx.hrttracker.ui.main.MainViewModel
import com.mkx.hrttracker.ui.navigation.sharedAxisXEnterFadeEasing
import com.mkx.hrttracker.ui.navigation.sharedAxisXEnterOffset
import com.mkx.hrttracker.ui.navigation.sharedAxisXEnterTransition
import com.mkx.hrttracker.ui.navigation.sharedAxisXExitFadeEasing
import com.mkx.hrttracker.ui.navigation.sharedAxisXExitTransition
import com.mkx.hrttracker.ui.navigation.sharedAxisXSlideDistancePx
import com.mkx.hrttracker.ui.navigation.sharedAxisXSlideEasing
import com.mkx.hrttracker.ui.navigation.sharedAxisXTransitionDurationMillis
import com.mkx.hrttracker.ui.navigation.topLevelFadeThroughExitEasing
import com.mkx.hrttracker.ui.navigation.topLevelTransitionDurationMillis
import com.mkx.hrttracker.ui.onboarding.OnboardingScreen
import com.mkx.hrttracker.ui.onboarding.OnboardingViewModel
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockScreen
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.security.appLockContentLayers
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider

const val EXTRA_HIGHLIGHT_KIND = "highlight_kind"
const val EXTRA_HIGHLIGHT_GROUP_UUID = "highlight_group_uuid"
const val EXTRA_HIGHLIGHT_SCHEDULE_TIME_UUID = "highlight_schedule_time_uuid"
const val EXTRA_HIGHLIGHT_SCHEDULED_AT = "highlight_scheduled_at"
const val EXTRA_HIGHLIGHT_MEDICATION_UUID = "highlight_medication_uuid"
const val EXTRA_HIGHLIGHT_ENTRY_UUID = "highlight_entry_uuid"
const val EXTRA_HIGHLIGHT_SCHEDULED_TARGETS = "highlight_scheduled_targets"
const val HIGHLIGHT_KIND_SCHEDULED = "scheduled"
const val HIGHLIGHT_KIND_MANUAL = "manual"

// Fade the splash out ourselves so the handoff is consistent across devices:
// some OEMs skip the default exit animation, which makes the swap to the
// onboarding hero icon read as a blink.
private const val SPLASH_EXIT_FADE_DURATION_MILLIS = 250L

data class ScheduledDoseRowHighlightTarget(
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID?,
    val scheduledAt: LocalDateTime,
)

fun ScheduledDoseRowHighlightTarget.toStorageValue(): String =
    listOf(
        groupUuid.toString(),
        scheduleTimeUuid?.toString().orEmpty(),
        scheduledAt.toString(),
    ).joinToString(separator = "|")

fun scheduledDoseRowHighlightTargetFromStorageValue(value: String): ScheduledDoseRowHighlightTarget? {
    val parts = value.split("|", limit = 3)
    if (parts.size != 3) {
        return null
    }
    return runCatching {
        ScheduledDoseRowHighlightTarget(
            groupUuid = UUID.fromString(parts[0]),
            scheduleTimeUuid = parts[1].takeIf(String::isNotBlank)?.let(UUID::fromString),
            scheduledAt = LocalDateTime.parse(parts[2]),
        )
    }.getOrNull()
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val appLockViewModel: AppLockViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var startupPreloaderProvider: Provider<StartupPreloader>

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    @Inject
    lateinit var reminderCapabilityReconciler: ReminderCapabilityReconciler

    override fun onCreate(savedInstanceState: Bundle?) {
        val startupTimingEnabled =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 ||
                packageName.endsWith(".benchmark")
        StartupTiming.reset(enabled = startupTimingEnabled)
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        parseWidgetHighlightIntent(intent)
        diagnosticsLogger.info(
            TAG,
            "main_activity_on_create_after_super " +
                "savedInstanceState=${savedInstanceState != null} startupTimingEnabled=$startupTimingEnabled"
        )
        splashScreen.setKeepOnScreenCondition {
            val appLockState = appLockViewModel.uiState.value
            val onboardingState = onboardingViewModel.uiState.value
            val shouldWaitForHomeShell = appLockState.isReady && !appLockState.shouldShowLockScreen
            !appLockState.isReady ||
                !onboardingState.isLoaded ||
                (shouldWaitForHomeShell && !mainViewModel.uiState.value.splashReady)
        }
        diagnosticsLogger.info(TAG, "main_activity_splash_condition_installed")
        splashScreen.setOnExitAnimationListener { splashProvider ->
            // Only cross-fade into onboarding, where the swap to the hero icon
            // would otherwise read as a blink. Once onboarding is complete the
            // home shell has no such handoff, so remove the splash immediately.
            if (onboardingViewModel.uiState.value.shouldShowOnboarding) {
                splashProvider.view
                    .animate()
                    .alpha(0f)
                    .setDuration(SPLASH_EXIT_FADE_DURATION_MILLIS)
                    .withEndAction { splashProvider.remove() }
                    .start()
            } else {
                splashProvider.remove()
            }
        }
        settingsRepository.refreshAppLanguageOption(this)
        diagnosticsLogger.info(TAG, "main_activity_language_refresh_requested")

        setContent {
            LaunchedEffect(Unit) {
                diagnosticsLogger.info(TAG, "main_activity_set_content_entered")
            }
            val settingsState by settingsRepository.settingsState.collectAsStateWithLifecycle()

            val isDarkTheme = settingsState.darkModeOption.resolveDarkTheme(isSystemInDarkTheme())

            DisposableEffect(settingsState.hideScreenContentEnabled) {
                applyHideScreenContent(enabled = settingsState.hideScreenContentEnabled)
                onDispose { }
            }

            DisposableEffect(isDarkTheme) {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(Color.Transparent.toArgb())
                } else {
                    SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                }

                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )

                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
                windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
                onDispose { }
            }

            HrtTrackerTheme(
                darkTheme = isDarkTheme,
                dynamicColor = settingsState.adaptiveColorEnabled
            ) {
                val navController = rememberNavController()
                val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
                val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
                val onboardingUiState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
                val homeDeepLinkSignal by mainViewModel.homeDeepLinkSignal.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)

                LaunchedEffect(
                    appLockUiState.isReady,
                    mainUiState.homeDataReady,
                    mainUiState.homeSource,
                ) {
                    if (!appLockUiState.isReady || !mainUiState.homeDataReady) {
                        return@LaunchedEffect
                    }
                    val nowAtTrigger = mainUiState.now
                    when (mainUiState.homeSource) {
                        HomeInputSource.SNAPSHOT -> {
                            diagnosticsLogger.info(
                                TAG,
                                "main_activity_home_data_ready source=snapshot now=$nowAtTrigger"
                            )
                            startupPreloaderProvider.get()
                                .startReminderRescheduleFromSnapshot(nowAtTrigger)
                        }
                        HomeInputSource.ROOM -> {
                            diagnosticsLogger.info(
                                TAG,
                                "main_activity_home_data_ready source=room now=$nowAtTrigger"
                            )
                            startupPreloaderProvider.get()
                                .startReminderRescheduleFromWarmDatabase(nowAtTrigger)
                        }
                        null -> Unit
                    }
                }

                LaunchedEffect(
                    appLockUiState.isReady,
                    appLockUiState.shouldShowLockScreen,
                    mainUiState.homeDataReady,
                ) {
                    if (
                        appLockUiState.isReady &&
                        !appLockUiState.shouldShowLockScreen &&
                        mainUiState.homeDataReady
                    ) {
                        withFrameNanos { }
                        diagnosticsLogger.info(TAG, "main_activity_first_home_frame_ready")
                        startupPreloaderProvider.get().startAfterFirstHomeFrame()
                    }
                }

                AppAuthenticationPromptEffect(
                    request = appLockUiState.pendingPrompt,
                    onAuthenticated = appLockViewModel::onAuthenticationSucceeded,
                    onError = appLockViewModel::onAuthenticationError
                )

                val homeShellReady = mainUiState.splashReady && onboardingUiState.isLoaded
                val contentLayers = appLockContentLayers(
                    appLockReady = appLockUiState.isReady,
                    shouldShowLockScreen = appLockUiState.shouldShowLockScreen,
                    homeShellReady = homeShellReady,
                )
                when {
                    contentLayers.showInWindowLockScreen && !contentLayers.showHomeShell -> {
                        BackHandler(enabled = true) { }
                        AppLockScreen(
                            errorMessageRes = appLockUiState.errorMessageRes,
                            onUnlockClick = appLockViewModel::requestUnlock
                        )
                    }
                    !contentLayers.showHomeShell -> Unit
                    else -> {
                        val hiddenHomeOffsetPx = sharedAxisXEnterOffset(
                            slideDistancePx = sharedAxisXSlideDistancePx(density),
                            forward = true,
                            layoutDirection = layoutDirection,
                        )
                        val homeOffsetX by animateIntAsState(
                            targetValue = if (onboardingUiState.shouldShowOnboarding) {
                                hiddenHomeOffsetPx
                            } else {
                                0
                            },
                            animationSpec = tween(
                                durationMillis = sharedAxisXTransitionDurationMillis,
                                easing = sharedAxisXSlideEasing,
                            ),
                            label = "home-offset-x",
                        )
                        val homeAlpha by animateFloatAsState(
                            targetValue = if (onboardingUiState.shouldShowOnboarding) 0f else 1f,
                            animationSpec = tween(
                                durationMillis = sharedAxisXTransitionDurationMillis,
                                easing = if (onboardingUiState.shouldShowOnboarding) {
                                    sharedAxisXExitFadeEasing
                                } else {
                                    sharedAxisXEnterFadeEasing
                                },
                            ),
                            label = "home-alpha",
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = homeOffsetX.toFloat()
                                        alpha = homeAlpha
                                    }
                                    .then(
                                        if (contentLayers.showInWindowLockScreen) {
                                            Modifier.clearAndSetSemantics { }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                val highlightEffectsEnabled =
                                    !contentLayers.showInWindowLockScreen &&
                                        !onboardingUiState.shouldShowOnboarding
                                HrtTrackerApp(
                                    navController = navController,
                                    homeDeepLinkSignal = homeDeepLinkSignal,
                                    highlightEffectsEnabled = highlightEffectsEnabled,
                                )
                            }

                            AnimatedVisibility(
                                visible = onboardingUiState.shouldShowOnboarding,
                                modifier = Modifier.fillMaxSize(),
                                enter = sharedAxisXEnterTransition(
                                    density = density,
                                    layoutDirection = layoutDirection,
                                    forward = false,
                                ),
                                exit = sharedAxisXExitTransition(
                                    density = density,
                                    layoutDirection = layoutDirection,
                                    forward = true,
                                ),
                                label = "onboarding-overlay",
                            ) {
                                OnboardingScreen(
                                    onOpenPrivacyPolicy = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, privacyPolicyUrl.toUri())
                                        )
                                    },
                                    onCompleteEnabled = onboardingViewModel::completeWithRemindersEnabled,
                                    onCompleteDeclined = onboardingViewModel::completeWithRemindersDeclined,
                                )
                            }

                            BackHandler(enabled = contentLayers.showInWindowLockScreen) { }
                            AnimatedVisibility(
                                visible = contentLayers.showInWindowLockScreen,
                                modifier = Modifier.fillMaxSize(),
                                enter = EnterTransition.None,
                                exit = fadeOut(
                                    animationSpec = tween(
                                        durationMillis = topLevelTransitionDurationMillis,
                                        easing = topLevelFadeThroughExitEasing,
                                    )
                                ),
                                label = "lock-overlay",
                            ) {
                                AppLockScreen(
                                    errorMessageRes = appLockUiState.errorMessageRes,
                                    onUnlockClick = appLockViewModel::requestUnlock,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        diagnosticsLogger.info(TAG, "main_activity_on_start")
        appLockViewModel.onForegrounded()
        reminderCapabilityReconciler.requestReconcile("main_activity_on_start")
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            diagnosticsLogger.info(TAG, "main_activity_on_stop backgrounding=true")
            appLockViewModel.onBackgrounded()
        } else {
            diagnosticsLogger.info(TAG, "main_activity_on_stop backgrounding=false reason=configuration_change")
        }
        super.onStop()
    }

    private fun applyHideScreenContent(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!enabled)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseWidgetHighlightIntent(intent)
    }

    private fun parseWidgetHighlightIntent(intent: Intent) {
        val keys = parseDoseRowHighlightIntent(intent)
        if (keys.isNotEmpty()) {
            mainViewModel.requestDoseRowHighlight(keys)
        }
    }

    private fun parseDoseRowHighlightIntent(intent: Intent): List<DoseRowHighlightKey> {
        val kind = intent.getStringExtra(EXTRA_HIGHLIGHT_KIND)
        if (kind == HIGHLIGHT_KIND_SCHEDULED) {
            val targetKeys = intent.getStringArrayListExtra(EXTRA_HIGHLIGHT_SCHEDULED_TARGETS)
                .orEmpty()
                .mapNotNull { encoded ->
                    scheduledDoseRowHighlightTargetFromStorageValue(encoded)?.let { target ->
                        DoseRowHighlightKey.Scheduled(
                            groupUuid = target.groupUuid,
                            scheduleTimeUuid = target.scheduleTimeUuid,
                            scheduledAt = target.scheduledAt,
                            medicationUuid = null,
                        )
                    }
                }
            if (targetKeys.isNotEmpty()) {
                return targetKeys
            }
        }

        val key = when (kind) {
            HIGHLIGHT_KIND_MANUAL -> {
                val entryUuid = intent.uuidExtra(EXTRA_HIGHLIGHT_ENTRY_UUID) ?: return emptyList()
                DoseRowHighlightKey.Manual(entryUuid)
            }
            HIGHLIGHT_KIND_SCHEDULED -> {
                val groupUuid = intent.uuidExtra(EXTRA_HIGHLIGHT_GROUP_UUID) ?: return emptyList()
                val scheduledAt = intent.localDateTimeExtra(EXTRA_HIGHLIGHT_SCHEDULED_AT) ?: return emptyList()
                DoseRowHighlightKey.Scheduled(
                    groupUuid = groupUuid,
                    scheduleTimeUuid = intent.uuidExtra(EXTRA_HIGHLIGHT_SCHEDULE_TIME_UUID),
                    scheduledAt = scheduledAt,
                    medicationUuid = intent.uuidExtra(EXTRA_HIGHLIGHT_MEDICATION_UUID),
                )
            }
            else -> return emptyList()
        }
        return listOf(key)
    }

    private fun Intent.uuidExtra(key: String): UUID? =
        getStringExtra(key)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun Intent.localDateTimeExtra(key: String): LocalDateTime? =
        getStringExtra(key)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    private companion object {
        const val TAG = "MainActivity"
    }
}
