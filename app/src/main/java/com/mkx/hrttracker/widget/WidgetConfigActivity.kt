package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.components.LocalCjkTextOffsetEnabled
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockScreen
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Full-screen live-preview reconfigure UI, launched by the launcher's long-press
// "reconfigure" affordance (WIDGET_FEATURE_RECONFIGURABLE, API 31+). The window is
// opaque except for a wallpaper window (Theme.HrtTracker.WidgetConfig sets
// windowShowWallpaper with a transparent windowBackground; the screen punches the
// hole), which fixes the API<35 black nav bar the old translucent-dialog window had:
// the bars now sit over the opaque scaffold, as in MainActivity.
// Dose-widget settings are global, so for them the specific appWidgetId is echoed back
// only to satisfy the RESULT_OK contract on launchers that ignore
// configuration_optional and invoke this on first placement. ANCHOR mode is
// per-instance, so the id is observable state: a singleTop redelivery targeting a
// DIFFERENT widget (onNewIntent) retargets the whole window in place. The result
// defaults to RESULT_CANCELED and only flips to RESULT_OK once the user saves, so
// backing out of such a first-placement config removes the widget instead of silently
// keeping an unconfigured one.
@AndroidEntryPoint
class WidgetConfigActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var widgetAppearanceRepository: WidgetAppearanceRepository

    @Inject
    lateinit var widgetSnapshotStore: WidgetSnapshotStore

    @Inject
    lateinit var journalRepository: com.mkx.hrttracker.data.repository.JournalRepository

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    // Persist must outlive the activity: the activity can be destroyed mid-write (system
    // kill, config change), and a lifecycleScope write would be cancelled with it.
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    // This activity is exported and launcher-reachable (reconfigure / first placement)
    // and renders journal data — anchor names and dates in the picker, plus the dose
    // snapshot preview — so it must honor the in-app lock exactly like MainActivity:
    // the content composes only once the lock state is ready and unlocked.
    private val appLockViewModel: AppLockViewModel by viewModels()

    // Observable because this singleTop activity can be redelivered a configure intent for
    // a DIFFERENT widget while a retained instance targets the old one (onNewIntent below);
    // everything per-instance (config type, anchor seed, the screen itself) rekeys on it.
    private var appWidgetId by mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        applyEdgeToEdgeSystemBars()
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.getIntExtra(
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

        setContent {
            // Recomputed per target id: an onNewIntent retarget can in principle land on a
            // different provider type. getAppWidgetInfo is null for INVALID_APPWIDGET_ID or
            // direct/malformed launches (the activity is exported);
            // widgetConfigTypeForProvider falls back to medium then.
            val configType = remember(appWidgetId) {
                widgetConfigTypeForProvider(
                    AppWidgetManager.getInstance(this)
                        .getAppWidgetInfo(appWidgetId)?.provider?.className,
                )
            }
            // Collected (and the prompt effect installed) outside the loaded gate below so
            // the biometric prompt can run concurrently with the settings/anchors load
            // instead of serializing behind it.
            val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
            AppAuthenticationPromptEffect(
                request = appLockUiState.pendingPrompt,
                onAuthenticated = appLockViewModel::onAuthenticationSucceeded,
                onError = appLockViewModel::onAuthenticationError,
            )
            // One-shot read of the PERSISTED settings. We must NOT seed from
            // settingsRepository.settingsState: its eager initialValue is a placeholder
            // (scale/alpha 1.0, dark FOLLOW_SYSTEM) emitted before DataStore loads, and the
            // screen captures its inputs once via rememberSaveable -- so on a cold-start
            // reconfigure that would let Save overwrite real settings with defaults. Render
            // nothing until the persisted value resolves. The widget snapshot read is
            // best-effort: a null (failed read / never persisted) just makes the preview
            // fall back to the fabricated preview snapshot.
            val loadedState by produceState<LoadedConfigState?>(initialValue = null, appWidgetId) {
                // On an onNewIntent retarget the producer restarts but the previous value
                // survives; drop it so the screen unmounts (and cannot seed its saveables
                // from the OLD widget's initialAnchorId) until the new load lands.
                value = null
                value = try {
                    LoadedConfigState(
                        settings = settingsRepository.getCurrentSettings(),
                        // Unlike the settings read below (no safe placeholder -> finish), appearance HAS a
                        // safe fallback: Default is exactly what a never-configured widget renders.
                        appearance = runCatching {
                            // The read must not race the fire-and-forget startup migration
                            // (HomeWidgetManager.start): seeding Defaults here would let Save
                            // clobber the migrated values. Idempotent, so awaiting it is safe;
                            // a failure leaves the legacy keys intact, so proceeding with
                            // whatever the store holds is fine.
                            runCatching {
                                widgetAppearanceRepository.migrateFromLegacySettingsIfNeeded()
                            }.onFailure { error ->
                                if (error is CancellationException) throw error
                            }
                            widgetAppearanceRepository.currentEffective(null)
                        }.getOrElse { error ->
                            if (error is CancellationException) throw error
                            WidgetAppearance.Default
                        },
                        snapshot = runCatching { widgetSnapshotStore.readSnapshot() }
                            .getOrNull(),
                        anchors = if (configType == WidgetConfigType.ANCHOR) {
                            // Bounded await + snapshot fallback, same contract as the widget
                            // render paths: a reconfigure launched into a cold process must
                            // not see the not-loaded window as an empty anchor list, but a
                            // persistently-broken database must not strand this window blank
                            // forever (unbounded awaitTrackedDates suspends through the
                            // error window). The live re-seed below corrects the list if
                            // the database recovers while the window is up.
                            journalRepository
                                .awaitTrackedDatesOrSnapshot(ANCHOR_WIDGET_AWAIT_TIMEOUT_MS)
                        } else emptyList(),
                        initialAnchorId = if (configType == WidgetConfigType.ANCHOR) {
                            runCatching {
                                val glanceId = androidx.glance.appwidget.GlanceAppWidgetManager(
                                    this@WidgetConfigActivity,
                                ).getGlanceIdBy(appWidgetId)
                                // Read via getAppWidgetState — NOT getDataStore with a guessed
                                // fileKey. appWidgetId.toString() is not Glance's actual file
                                // key, so that path always reads an empty store and reconfigure
                                // never pre-selects. getAppWidgetState resolves the real store
                                // from the glanceId.
                                androidx.glance.appwidget.state.getAppWidgetState(
                                    this@WidgetConfigActivity,
                                    androidx.glance.state.PreferencesGlanceStateDefinition,
                                    glanceId,
                                ).anchorId()
                            }.getOrNull()
                        } else null,
                        initialBackgroundFlag = if (configType == WidgetConfigType.ANCHOR) {
                            runCatching {
                                val glanceId = androidx.glance.appwidget.GlanceAppWidgetManager(
                                    this@WidgetConfigActivity,
                                ).getGlanceIdBy(appWidgetId)
                                androidx.glance.appwidget.state.getAppWidgetState(
                                    this@WidgetConfigActivity,
                                    androidx.glance.state.PreferencesGlanceStateDefinition,
                                    glanceId,
                                ).backgroundFlag()
                            }.getOrNull()
                        } else null,
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
                // Keep the anchor list LIVE after the one-shot seed. This window is
                // singleTop in its own task, so a second widget tap can resume a retained
                // instance without recreating it — a one-shot list captured before the
                // user added their first date would leave the picker on "No tracked dates
                // yet." forever. Settings/appearance stay one-shot by design (see above);
                // only the anchors may re-seed. On the normal path the DB is already open
                // here (the bounded await above), so this flow emits immediately; after a
                // timed-out seed it emits once the database recovers.
                if (configType == WidgetConfigType.ANCHOR) {
                    journalRepository.observeLoadedTrackedDates().collect { dates ->
                        value = value?.copy(anchors = dates)
                    }
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
                        when {
                        // Lock state not yet known: stay blank (same as the pre-load
                        // window) rather than flashing anchors that may need to hide.
                        !appLockUiState.isReady -> Unit

                        appLockUiState.shouldShowLockScreen -> AppLockScreen(
                            errorMessageRes = appLockUiState.errorMessageRes,
                            onUnlockClick = appLockViewModel::requestUnlock,
                        )

                        // key(appWidgetId) is load-bearing: without it, a retarget that
                        // unmounts and remounts the screen at the same position would let
                        // rememberSaveable RESTORE the previous widget's selection instead
                        // of re-seeding from the new widget's persisted state.
                        else -> key(appWidgetId) {
                            WidgetConfigScreen(
                                initialAppearance = loaded.appearance,
                                configType = configType,
                                appWidgetId = appWidgetId,
                                snapshot = loaded.snapshot,
                                anchors = loaded.anchors,
                                initialAnchorId = loaded.initialAnchorId,
                                initialBackgroundFlag = loaded.initialBackgroundFlag,
                                today = java.time.LocalDate.now(),
                                onSaveAnchor = { appearance, anchorId, backgroundFlag ->
                                    // Captured NOW: onNewIntent can retarget the mutable
                                    // appWidgetId while these writes are in flight, and the
                                    // selection must land on the widget whose screen was saved.
                                    val targetWidgetId = appWidgetId
                                    appScope.launch {
                                        var persisted = false
                                        try {
                                            // Appearance is global with a safe fallback:
                                            // best-effort, must not block placement.
                                            runCatching {
                                                widgetAppearanceRepository.setDefault(appearance)
                                            }
                                            val glanceId = runCatching {
                                                androidx.glance.appwidget
                                                    .GlanceAppWidgetManager(this@WidgetConfigActivity)
                                                    .getGlanceIdBy(targetWidgetId)
                                                    .also { id ->
                                                        writeAnchorId(
                                                            this@WidgetConfigActivity, id, anchorId,
                                                        )
                                                        writeBackgroundFlag(
                                                            this@WidgetConfigActivity, id, backgroundFlag,
                                                        )
                                                    }
                                            }.onFailure { throwable ->
                                                if (throwable is CancellationException) {
                                                    throw throwable
                                                }
                                                diagnosticsLogger.warning(
                                                    TAG,
                                                    "anchor_config_save_failed",
                                                    throwable,
                                                )
                                            }.getOrNull()
                                            persisted = glanceId != null
                                            // Repaint is best-effort once the state is written:
                                            // the widget IS configured, and the manager /
                                            // date-receiver paths repaint it if this fails.
                                            // Synchronous push, NOT a bare session update():
                                            // finish() below backgrounds the process, which is
                                            // exactly where a Glance session recomposition
                                            // stalls (see updateAllAnchorWidgets) — a bare
                                            // update left the launcher on the old anchor until
                                            // an unrelated broadcast repainted it.
                                            if (glanceId != null) {
                                                runCatching {
                                                    updateAllAnchorWidgets(applicationContext)
                                                }
                                            }
                                        } finally {
                                            withContext(NonCancellable + Dispatchers.Main) {
                                                // If onNewIntent retargeted mid-save, this window
                                                // now hosts a different widget's config: its result
                                                // contract belongs to the new id, and finishing
                                                // would close it under the user.
                                                if (appWidgetId == targetWidgetId) {
                                                    // The anchor id IS the required configuration:
                                                    // confirm placement only once it is persisted.
                                                    // Otherwise keep the default RESULT_CANCELED so
                                                    // a failed first placement removes the widget
                                                    // (retry) instead of leaving a confirmed-but-
                                                    // unconfigured empty state on the launcher.
                                                    if (persisted) {
                                                        setResult(
                                                            RESULT_OK,
                                                            Intent().putExtra(
                                                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                                targetWidgetId,
                                                            ),
                                                        )
                                                    }
                                                    finish()
                                                }
                                            }
                                        }
                                    }
                                },
                                onAddAnchor = { name, icon, date, paletteKey, pinned ->
                                    appScope.launch {
                                        journalRepository.addTrackedDate(
                                            name = name,
                                            icon = icon,
                                            date = date,
                                            paletteKey = paletteKey,
                                            pinned = pinned,
                                        )
                                    }
                                },
                                onSave = { appearance ->
                                    setResult(
                                        RESULT_OK,
                                        Intent().putExtra(
                                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                                            appWidgetId,
                                        ),
                                    )
                                    appScope.launch {
                                        try {
                                            // The screen now owns all six appearance fields,
                                            // so a wholesale setDefault is correct here. A write
                                            // failure is caught (not rethrown): appScope has no
                                            // exception handler, so letting it propagate after
                                            // finish() would crash the process even though the
                                            // launcher was already told RESULT_OK.
                                            runCatching {
                                                widgetAppearanceRepository.setDefault(appearance)
                                            }.onFailure { throwable ->
                                                if (throwable is CancellationException) {
                                                    throw throwable
                                                }
                                                diagnosticsLogger.warning(
                                                    TAG,
                                                    "widget_config_save_failed",
                                                    throwable,
                                                )
                                            }
                                        } finally {
                                            // Finish only after the write lands: RESULT_OK is
                                            // already reported, and finishing first would leave
                                            // a window where process death right after finish()
                                            // loses the save the launcher believes succeeded.
                                            withContext(NonCancellable + Dispatchers.Main) {
                                                finish()
                                            }
                                        }
                                    }
                                },
                                onCancel = { finish() },
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    // singleTop redelivery: a launcher can hand a backgrounded, retained instance a
    // configure intent for a DIFFERENT widget. Retarget in place: reset the result
    // contract for the new id and let the observable appWidgetId rekey the load and the
    // screen (discarding the old instance's selection). A same-id redelivery is a plain
    // resume — the retained state is exactly right, so nothing is touched.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newAppWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (newAppWidgetId != appWidgetId) {
            setResult(
                RESULT_CANCELED,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newAppWidgetId),
            )
            appWidgetId = newAppWidgetId
        }
    }

    override fun onStart() {
        super.onStart()
        appLockViewModel.onForegrounded()
    }

    override fun onStop() {
        // Mirrors MainActivity: a configuration change is not a real backgrounding, so it
        // must not re-lock (the grace period would otherwise punish a font-scale change).
        if (!isChangingConfigurations) {
            appLockViewModel.onBackgrounded()
        }
        super.onStop()
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

    private companion object {
        const val TAG = "WidgetConfigActivity"
    }
}

// Both one-shot loads the screen seeds from, resolved together so the UI renders once.
// anchors/initialAnchorId are populated only in ANCHOR mode (empty/null otherwise).
private data class LoadedConfigState(
    val settings: SettingsState,
    val appearance: WidgetAppearance,
    val snapshot: WidgetSnapshotRecord?,
    val anchors: List<com.mkx.hrttracker.model.journal.TrackedDate> = emptyList(),
    val initialAnchorId: String? = null,
    val initialBackgroundFlag: com.mkx.hrttracker.model.journal.PrideFlag? = null,
)

enum class WidgetConfigType { MEDIUM, LARGE, ANCHOR }

// Resolves the launching widget's config type from its provider class. A null/unknown
// provider (malformed or direct launch — the activity is exported) falls back to MEDIUM,
// matching the prior isMediumWidgetProvider behaviour.
internal fun widgetConfigTypeForProvider(providerClassName: String?): WidgetConfigType = when (providerClassName) {
    HrtWidgetLargeReceiver::class.java.name -> WidgetConfigType.LARGE
    HrtAnchorWidgetReceiver::class.java.name -> WidgetConfigType.ANCHOR
    else -> WidgetConfigType.MEDIUM
}
