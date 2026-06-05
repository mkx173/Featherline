package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.components.LocalCjkTextOffsetEnabled
import com.mkx.hrttracker.ui.settings.WidgetAppearanceDialog
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

// Hosts only the widget-appearance dialog over a translucent window, launched by the
// launcher's long-press "reconfigure" affordance (WIDGET_FEATURE_RECONFIGURABLE, API 31+).
// Widget settings are global, so the specific appWidgetId is irrelevant to what we edit; it
// is read only to satisfy the first-placement RESULT_OK contract on launchers that ignore
// configuration_optional.
@AndroidEntryPoint
class WidgetConfigActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    // Persist must outlive the activity: the dialog's Save calls onAppearanceChange then
    // onDismiss (finish) synchronously, so a lifecycleScope write would be cancelled mid-edit.
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // configuration_optional should prevent first-placement invocation on API 31+, but be
        // safe on launchers that ignore it: returning RESULT_OK keeps the placed widget.
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )

        setContent {
            // One-shot read of the PERSISTED settings. We must NOT seed from
            // settingsRepository.settingsState: its eager initialValue is a placeholder
            // (scale/alpha 1.0, dark FOLLOW_SYSTEM) emitted before DataStore loads, and the
            // dialog captures its inputs once via remember -- so on a cold-start reconfigure
            // that would let Save overwrite real settings with defaults. Render nothing until
            // the persisted value resolves.
            val settings by produceState<SettingsState?>(initialValue = null) {
                value = settingsRepository.getCurrentSettings()
            }
            settings?.let { loaded ->
                HrtTrackerTheme(
                    darkTheme = loaded.darkModeOption.resolveDarkTheme(isSystemInDarkTheme()),
                    dynamicColor = loaded.adaptiveColorEnabled,
                    amoled = loaded.pureBlackEnabled,
                ) {
                    CompositionLocalProvider(
                        LocalCjkTextOffsetEnabled provides loaded.cjkTextOffsetEnabled,
                    ) {
                        WidgetAppearanceDialog(
                            contentScale = loaded.widgetContentScale,
                            backgroundAlpha = loaded.widgetBackgroundAlpha,
                            darkModeOption = loaded.widgetDarkModeOption,
                            onAppearanceChange = { scale, alpha, darkMode ->
                                appScope.launch {
                                    settingsRepository.setWidgetAppearance(scale, alpha, darkMode)
                                }
                            },
                            onDismiss = { finish() },
                        )
                    }
                }
            }
        }
    }
}
