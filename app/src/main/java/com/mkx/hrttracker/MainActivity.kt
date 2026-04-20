package com.mkx.hrttracker

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.appcompat.app.AppCompatActivity
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.ui.HrtTrackerApp
import com.mkx.hrttracker.ui.history.HistoryViewModel
import com.mkx.hrttracker.ui.main.MainViewModel
import com.mkx.hrttracker.ui.plan.PlanViewModel
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockScreen
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.activity.viewModels

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val appLockViewModel: AppLockViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            mainViewModel.uiState.value.isLoading || !appLockViewModel.uiState.value.isReady
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

                hiltViewModel<PlanViewModel>(viewModelStoreOwner = this@MainActivity)
                hiltViewModel<HistoryViewModel>(viewModelStoreOwner = this@MainActivity)

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
                    else -> HrtTrackerApp(navController = navController)
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
