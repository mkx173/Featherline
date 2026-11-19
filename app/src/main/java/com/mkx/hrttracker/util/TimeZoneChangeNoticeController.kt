package com.mkx.hrttracker.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class TimeZoneChangeNotice(
    val previousZoneId: String,
    val currentZoneId: String,
)

@Singleton
class TimeZoneChangeNoticeController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appTimeSource: AppTimeSource,
    @param:AppScope private val scope: CoroutineScope,
) {
    // Hides the change the user just dismissed, so the banner goes at once instead
    // of after the DataStore round-trip. Keyed on both zones, so another change
    // still shows. Cleared once the acknowledgement reaches the settings flow, so a
    // restore that brings the same zones back shows the banner again. A failed
    // acknowledgement clears it at once: nothing was stored, so the banner comes back.
    private val dismissedNotice = MutableStateFlow<TimeZoneChangeNotice?>(null)

    val notice: StateFlow<TimeZoneChangeNotice?> = combine(
        appTimeSource.currentZone
            .map { it.id }
            .distinctUntilChanged(),
        settingsRepository.settingsState
            .map { it.lastSeenTimeZoneId }
            .distinctUntilChanged(),
        dismissedNotice,
    ) { current, stored, dismissed ->
        when {
            stored == null -> null
            stored == current -> null
            else -> TimeZoneChangeNotice(previousZoneId = stored, currentZoneId = current)
                .takeIf { it != dismissed }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(TIME_ZONE_NOTICE_STOP_TIMEOUT_MILLIS), null)

    fun attachToProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                onAppForegrounded()
            }
        })
    }

    internal fun onAppForegrounded() {
        val current = ZoneId.systemDefault().id
        scope.launch {
            // settingsState is Eagerly-started with a default SettingsState whose
            // lastSeenTimeZoneId is null. On cold start ON_START can fire before
            // DataStore has emitted the persisted value, so reading settingsState.value
            // here would briefly see null even when a previous zone is on disk —
            // and we'd silently overwrite it, suppressing the very banner this
            // controller exists to surface. getCurrentSettings() suspends until
            // the DataStore read completes, so the null branch only fires for
            // a genuinely-empty store (fresh install / pre-feature backup).
            runSettingsCall {
                val stored = settingsRepository.getCurrentSettings().lastSeenTimeZoneId
                if (stored == null) {
                    settingsRepository.acknowledgeTimeZone(current)
                }
            }
        }
    }

    fun dismiss() {
        val current = appTimeSource.currentZone.value.id
        val stored = settingsRepository.settingsState.value.lastSeenTimeZoneId
        val dismissed = stored?.takeIf { it != current }
            ?.let { TimeZoneChangeNotice(previousZoneId = it, currentZoneId = current) }
        if (dismissed != null) {
            dismissedNotice.value = dismissed
        }
        scope.launch {
            val acknowledged = runSettingsCall { settingsRepository.acknowledgeTimeZone(current) }
            if (dismissed == null) return@launch
            if (acknowledged) {
                settingsRepository.settingsState.first {
                    it.lastSeenTimeZoneId != dismissed.previousZoneId
                }
            }
            dismissedNotice.compareAndSet(dismissed, null)
        }
    }

    // AppScope has no exception handler, so a settings read or write that fails
    // here would end the process. A failure leaves the stored zone as it was.
    private suspend fun runSettingsCall(block: suspend () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            false
        }
    }
}

private const val TIME_ZONE_NOTICE_STOP_TIMEOUT_MILLIS = 5_000L
