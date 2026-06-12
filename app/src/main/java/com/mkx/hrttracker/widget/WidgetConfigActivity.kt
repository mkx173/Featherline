package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.components.LocalCjkTextOffsetEnabled
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

// Full-screen live-preview reconfigure UI, launched by the launcher's long-press
// "reconfigure" affordance (WIDGET_FEATURE_RECONFIGURABLE, API 31+). The window is
// opaque except for a wallpaper window (Theme.HrtTracker.WidgetConfig sets
// windowShowWallpaper with a transparent windowBackground; the screen punches the
// hole), which fixes the API<35 black nav bar the old translucent-dialog window had:
// the bars now sit over the opaque scaffold, as in MainActivity.
// Widget settings are global, so the specific appWidgetId is irrelevant to what we
// edit; it is echoed back only to satisfy the RESULT_OK contract on launchers that
// ignore configuration_optional and invoke this on first placement. The result
// defaults to RESULT_CANCELED and only flips to RESULT_OK once the user saves, so
// backing out of such a first-placement config removes the widget instead of silently
// keeping an unconfigured one.
@AndroidEntryPoint
class WidgetConfigActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var widgetSnapshotStore: WidgetSnapshotStore

    // Persist must outlive the activity: Save calls setWidgetAppearance then finish()
    // synchronously, so a lifecycleScope write would be cancelled mid-edit.
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        applyEdgeToEdgeSystemBars()
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Default to CANCELED; Save flips this to RESULT_OK below. On a launcher that ignores
        // configuration_optional and invokes this on first placement, this makes Cancel/Back
        // actually cancel the placement rather than confirm it.
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        // getAppWidgetInfo is null for INVALID_APPWIDGET_ID or direct/malformed launches
        // (the activity is exported); isMediumWidgetProvider falls back to medium then.
        val isMediumWidget = isMediumWidgetProvider(
            AppWidgetManager.getInstance(this)
                .getAppWidgetInfo(appWidgetId)?.provider?.className,
        )

        setContent {
            // One-shot read of the PERSISTED settings. We must NOT seed from
            // settingsRepository.settingsState: its eager initialValue is a placeholder
            // (scale/alpha 1.0, dark FOLLOW_SYSTEM) emitted before DataStore loads, and the
            // screen captures its inputs once via rememberSaveable -- so on a cold-start
            // reconfigure that would let Save overwrite real settings with defaults. Render
            // nothing until the persisted value resolves. The widget snapshot read is
            // best-effort: a null (failed read / never persisted) just makes the preview
            // fall back to the fabricated preview snapshot.
            val loadedState by produceState<LoadedConfigState?>(initialValue = null) {
                value = try {
                    LoadedConfigState(
                        settings = settingsRepository.getCurrentSettings(),
                        snapshot = runCatching { widgetSnapshotStore.readSnapshot() }
                            .getOrNull(),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    // getCurrentSettings reads DataStore directly, bypassing the repository's
                    // IOException .catch. If that read fails we cannot seed the controls with
                    // real values (and must not seed placeholders -- Save would clobber
                    // persisted settings), so close the window instead of stranding it.
                    finish()
                    return@produceState
                }
            }
            loadedState?.let { loaded ->
                HrtTrackerTheme(
                    darkTheme = loaded.settings.darkModeOption
                        .resolveDarkTheme(isSystemInDarkTheme()),
                    dynamicColor = loaded.settings.adaptiveColorEnabled,
                    amoled = loaded.settings.pureBlackEnabled,
                ) {
                    CompositionLocalProvider(
                        LocalCjkTextOffsetEnabled provides loaded.settings.cjkTextOffsetEnabled,
                    ) {
                        WidgetConfigScreen(
                            initialContentScale = loaded.settings.widgetContentScale,
                            initialBackgroundAlpha = loaded.settings.widgetBackgroundAlpha,
                            initialDarkModeOption = loaded.settings.widgetDarkModeOption,
                            isMediumWidget = isMediumWidget,
                            appWidgetId = appWidgetId,
                            snapshot = loaded.snapshot,
                            onSave = { scale, alpha, darkMode ->
                                setResult(
                                    RESULT_OK,
                                    Intent().putExtra(
                                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                                        appWidgetId,
                                    ),
                                )
                                appScope.launch {
                                    settingsRepository.setWidgetAppearance(
                                        scale,
                                        alpha,
                                        darkMode,
                                    )
                                }
                                finish()
                            },
                            onCancel = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun applyEdgeToEdgeSystemBars() {
        val barStyle = SystemBarStyle.auto(
            Color.Transparent.toArgb(),
            Color.Transparent.toArgb(),
        )
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        disableNavigationBarContrast()
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}

// Both one-shot loads the screen seeds from, resolved together so the UI renders once.
private data class LoadedConfigState(
    val settings: SettingsState,
    val snapshot: WidgetSnapshotRecord?,
)

// Resolves the launching widget's size from its provider class. Anything that is not
// explicitly the large receiver — including a null provider from a malformed or direct
// launch (the activity is exported and tolerates INVALID_APPWIDGET_ID) — falls back to
// the medium preview rather than failing.
internal fun isMediumWidgetProvider(providerClassName: String?): Boolean =
    providerClassName != HrtWidgetLargeReceiver::class.java.name
