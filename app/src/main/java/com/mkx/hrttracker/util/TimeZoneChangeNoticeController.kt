package com.mkx.hrttracker.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mkx.hrttracker.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Re-emits on every ON_START so we re-read the device zone after a possible
    // OS-level change. The underlying value source (ZoneId.systemDefault()) is
    // not reactive, so we drive the flow manually.
    private val deviceZoneId = MutableStateFlow(ZoneId.systemDefault().id)

    // Suppresses the banner for a previous zone the user has already dismissed
    // in this process. Cleared once acknowledgeTimeZone propagates to the
    // settings flow, but tracked locally so the banner disappears immediately
    // on dismiss without waiting for the DataStore round-trip.
    private val dismissedPreviousZoneId = MutableStateFlow<String?>(null)

    val notice: StateFlow<TimeZoneChangeNotice?> = combine(
        deviceZoneId,
        settingsRepository.settingsState
            .map { it.lastSeenTimeZoneId }
            .distinctUntilChanged(),
        dismissedPreviousZoneId,
    ) { current, stored, dismissed ->
        when {
            stored == null -> null
            stored == current -> null
            dismissed == stored -> null
            else -> TimeZoneChangeNotice(previousZoneId = stored, currentZoneId = current)
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun attachToProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                onAppForegrounded()
            }
        })
    }

    private fun onAppForegrounded() {
        val current = ZoneId.systemDefault().id
        deviceZoneId.value = current
        scope.launch {
            // settingsState is Eagerly-started with a default SettingsState whose
            // lastSeenTimeZoneId is null. On cold start ON_START can fire before
            // DataStore has emitted the persisted value, so reading settingsState.value
            // here would briefly see null even when a previous zone is on disk —
            // and we'd silently overwrite it, suppressing the very banner this
            // controller exists to surface. getCurrentSettings() suspends until
            // the DataStore read completes, so the null branch only fires for
            // a genuinely-empty store (fresh install / pre-feature backup).
            val stored = settingsRepository.getCurrentSettings().lastSeenTimeZoneId
            if (stored == null) {
                settingsRepository.acknowledgeTimeZone(current)
            }
        }
    }

    fun dismiss() {
        val current = deviceZoneId.value
        val stored = settingsRepository.settingsState.value.lastSeenTimeZoneId
        if (stored != null) {
            dismissedPreviousZoneId.value = stored
        }
        scope.launch {
            settingsRepository.acknowledgeTimeZone(current)
        }
    }
}
