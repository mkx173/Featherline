package com.mkx.hrttracker.startup

import android.os.SystemClock
import android.util.Log
import java.util.Locale

internal object StartupTiming {
    private const val TAG = "HrtStartupTiming"
    private val markedEvents = linkedSetOf<String>()
    private var enabled = false
    private var startElapsedNanos = 0L

    @Synchronized
    fun reset(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) return
        startElapsedNanos = SystemClock.elapsedRealtimeNanos()
        markedEvents.clear()
        markLocked("activity_on_create")
    }

    @Synchronized
    fun mark(event: String) {
        if (!enabled || startElapsedNanos == 0L || !markedEvents.add(event)) return
        markLocked(event)
    }

    private fun markLocked(event: String) {
        val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - startElapsedNanos) / 1_000_000.0
        Log.i(TAG, "$event elapsedMs=${String.format(Locale.US, "%.1f", elapsedMillis)}")
    }
}
