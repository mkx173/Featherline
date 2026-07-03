package com.mkx.hrttracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
        // The frost card's chrome flips with the system via day/night colour providers; only
        // its baked aurora-bloom bitmap keeps the composing mode's light/dark tuning.
        // Re-render on a night flip so the blooms re-tune. ponytail: only works while the
        // process is alive; a dead process just shows the other mode's (still readable)
        // bloom tuning until its next re-render (journal edit, daily refresh, app open).
        var wasNight = context.isNightMode()
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    val night = receiverContext.isNightMode()
                    if (night == wasNight) return
                    wasNight = night
                    appScope.launch {
                        runCatching { updateAllAnchorWidgets(context) }
                            .onFailure { throwable ->
                                if (throwable is CancellationException) throw throwable
                                diagnosticsLogger.warning(TAG, "anchor_night_refresh_failed", throwable)
                            }
                    }
                }
            },
            IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private companion object {
        const val TAG = "AnchorWidgetManager"
    }
}

private fun Context.isNightMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
