package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Counts reasons the top-level navigation chrome must ignore taps: an open
 * modal window (sheet or dialog) or an in-flight mutation. Modal windows
 * already block taps behind their scrim; this lock closes the transition
 * windows around them — the frames while a sheet is appearing where a
 * simultaneous chrome tap could still navigate, and the gap after a confirm
 * dialog closes while the confirmed work is still being written.
 */
@Stable
class NavigationLockState {
    private val lockCount = mutableIntStateOf(0)

    val isLocked: Boolean
        get() = lockCount.intValue > 0

    fun acquire() {
        lockCount.intValue += 1
    }

    fun release() {
        // An unmatched release would drive the count negative and silently
        // disarm the lock for the rest of the session; fail loud instead.
        check(lockCount.intValue > 0) {
            "NavigationLockState.release() called without a matching acquire()"
        }
        lockCount.intValue -= 1
    }
}

// The default instance keeps previews, tests, and compositions outside the
// app shell (e.g. onboarding) working without an explicit provider.
val LocalNavigationLock = staticCompositionLocalOf { NavigationLockState() }

/** Holds the navigation lock while [active] and this composable is composed. */
@Composable
fun NavigationLockEffect(active: Boolean) {
    val lock = LocalNavigationLock.current
    DisposableEffect(lock, active) {
        if (active) {
            lock.acquire()
        }
        onDispose {
            if (active) {
                lock.release()
            }
        }
    }
}
