package com.mkx.hrttracker

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.startup.StartupPreloader
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.ui.HrtTrackerApp
import com.mkx.hrttracker.ui.main.MainViewModel
import com.mkx.hrttracker.ui.navigation.Screen
import com.mkx.hrttracker.ui.onboarding.OnboardingScreen
import com.mkx.hrttracker.ui.onboarding.OnboardingViewModel
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockScreen
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider

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

    override fun onCreate(savedInstanceState: Bundle?) {
        val startupTimingEnabled =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 ||
                packageName.endsWith(".benchmark")
        StartupTiming.reset(enabled = startupTimingEnabled)
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
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
                val context = LocalContext.current
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

                when {
                    !appLockUiState.isReady -> Unit
                    appLockUiState.shouldShowLockScreen -> {
                        AppLockScreen(
                            errorMessageRes = appLockUiState.errorMessageRes,
                            onUnlockClick = appLockViewModel::requestUnlock
                        )
                    }
                    !mainUiState.splashReady -> Unit
                    !onboardingUiState.isLoaded -> Unit
                    else -> {
                        HrtTrackerApp(navController = navController)
                        if (onboardingUiState.shouldShowOnboarding) {
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
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        diagnosticsLogger.info(TAG, "main_activity_on_start")
        appLockViewModel.onForegrounded()
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

    private companion object {
        const val TAG = "MainActivity"
    }
}
