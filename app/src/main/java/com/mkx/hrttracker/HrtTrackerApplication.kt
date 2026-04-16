package com.mkx.hrttracker

import android.app.Application
import android.app.UiModeManager
import com.mkx.hrttracker.data.AppContainer
import com.mkx.hrttracker.data.DefaultAppContainer
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.util.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HrtTrackerApplication : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Collect the settings state flow
        applicationScope.launch {
            appContainer.settingsRepository.settingsState.collect { settings ->
                // Access the dark mode option
                val darkModeOption = settings.darkModeOption

                getSystemService(UiModeManager::class.java).setApplicationNightMode(
                    when (darkModeOption) {
                        DarkModeOption.DARK -> UiModeManager.MODE_NIGHT_YES
                        DarkModeOption.LIGHT -> UiModeManager.MODE_NIGHT_NO
                        DarkModeOption.FOLLOW_SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    }
                )
            }
        }
        ToastManager.init(this)
    }
}
