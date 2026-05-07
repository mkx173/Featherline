package com.mkx.hrttracker

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.mkx.hrttracker.ui.onboarding.OnboardingDialogs
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockScreen
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val appLockViewModel: AppLockViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var startupPreloaderProvider: Provider<StartupPreloader>

    override fun onCreate(savedInstanceState: Bundle?) {
        val startupTimingEnabled =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 ||
                packageName.endsWith(".benchmark")
        StartupTiming.reset(enabled = startupTimingEnabled)
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            val appLockState = appLockViewModel.uiState.value
            val shouldWaitForHomeShell = appLockState.isReady && !appLockState.shouldShowLockScreen
            !appLockState.isReady || (shouldWaitForHomeShell && !mainViewModel.uiState.value.splashReady)
        }
        settingsRepository.refreshAppLanguageOption(this)

        setContent {
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

                LaunchedEffect(
                    appLockUiState.isReady,
                    mainUiState.homeDataReady,
                    mainUiState.homeSource,
                    mainUiState.now,
                ) {
                    if (!appLockUiState.isReady || !mainUiState.homeDataReady) {
                        return@LaunchedEffect
                    }
                    when (mainUiState.homeSource) {
                        HomeInputSource.SNAPSHOT -> {
                            startupPreloaderProvider.get()
                                .startReminderRescheduleFromSnapshot(mainUiState.now)
                        }
                        HomeInputSource.ROOM -> {
                            startupPreloaderProvider.get()
                                .startReminderRescheduleFromWarmDatabase(mainUiState.now)
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
                    else -> {
                        HrtTrackerApp(navController = navController)
                        OnboardingDialogs()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLockViewModel.onForegrounded()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            appLockViewModel.onBackgrounded()
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
}
