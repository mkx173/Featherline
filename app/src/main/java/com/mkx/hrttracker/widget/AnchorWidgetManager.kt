package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// Keeps the anchor surfaces in step with the journal. Observes the anchor ids+dates and,
// on any add/edit/delete, re-renders the widgets and refreshes the pinned shortcuts (which
// also disables orphans). Isolated from HomeWidgetManager so dose-snapshot concerns and
// anchor concerns stay decoupled. Started once from the Application.
@Singleton
class AnchorWidgetManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val journalRepository: JournalRepository,
    private val widgetAppearanceRepository: WidgetAppearanceRepository,
    @param:AppScope private val appScope: CoroutineScope,
    private val diagnosticsLogger: AppDiagnosticsLogger,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            // Loaded flow only: the not-loaded window must not reach the surfaces (it reads
            // as "all anchors deleted"), and this collector must not force the DB open on
            // every process spawn — the first emission arrives once the UI or a widget
            // refresh opens it.
            journalRepository.observeLoadedTrackedDates()
                // De-dupe on the whole list: every field the surfaces render — name, icon,
                // palette, date — must trigger a refresh, so compare full TrackedDates
                // (data-class equality), never a (id, date) projection that would silently
                // drop rename/icon/palette edits. First emission kept (no .drop(1)) so the
                // surfaces are correct after a process restart.
                .distinctUntilChanged()
                .catch { throwable ->
                    diagnosticsLogger.warning(TAG, "anchor_observe_failed", throwable)
                }
                .collect {
                    runCatching {
                        updateAllAnchorWidgets(context)
                        AnchorShortcutManager.refreshAll(context)
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        diagnosticsLogger.warning(TAG, "anchor_refresh_failed", throwable)
                    }
                }
        }

        // Second collector: the appearance is GLOBAL (setDefault), but the config Activity
        // only repaints its own glanceId — so an anchor/dose config save, or a settings-screen
        // appearance change, would leave every OTHER anchor instance on stale RemoteViews until
        // an unrelated journal/date refresh. Mirrors HomeWidgetManager's appearance collector
        // (which does the same for the dose widgets). Shortcuts are deliberately NOT refreshed
        // here: AnchorIconRenderer bakes only the palette gradient + day count, never the widget
        // appearance (seed/adaptive/alpha/scale/dark), so an appearance change can't stale them.
        appScope.launch {
            widgetAppearanceRepository.observeChanges()
                // Drop the replayed current value: the surfaces are already correct from
                // provideGlance on process start, so only real changes should repaint.
                .drop(1)
                // Terminal guard: observeChanges() rethrows non-IOExceptions by design, which
                // would otherwise crash the handler-less appScope (mirrors HomeWidgetManager).
                .catch { throwable ->
                    diagnosticsLogger.warning(TAG, "anchor_appearance_stream_failed", throwable)
                }
                .collect {
                    runCatching {
                        updateAllAnchorWidgets(context)
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        diagnosticsLogger.warning(TAG, "anchor_appearance_refresh_failed", throwable)
                    }
                }
        }
    }

    private companion object {
        const val TAG = "AnchorWidgetManager"
    }
}
