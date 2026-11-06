package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Counts reasons the top-level navigation chrome must ignore taps: an open
 * modal window (sheet or dialog) or an in-flight mutation. Modal windows
 * already block taps behind their scrim; this lock closes the transition
 * windows around them — the frames while a sheet is appearing where a
 * simultaneous chrome tap could still navigate, and the gap after a confirm
 * dialog closes while the confirmed work is still being written.
 *
 * A deep link taking the user elsewhere waits only for the mutations ([isBusy]):
 * it closes open modal windows instead, through [dismissModals].
 */
@Stable
class NavigationLockState {
    private val lockCount = mutableIntStateOf(0)
    private val busyCount = mutableIntStateOf(0)
    private val modalDismissSignal = mutableIntStateOf(0)

    val isLocked: Boolean
        get() = lockCount.intValue > 0

    /** Whether a mutation holds the lock, not only an open modal window. */
    val isBusy: Boolean
        get() = busyCount.intValue > 0

    /** Bumped by [dismissModals]; each modal window closes on a bump after it opened. */
    val modalDismissRequest: Int
        get() = modalDismissSignal.intValue

    fun acquire(busy: Boolean = true) {
        lockCount.intValue += 1
        if (busy) busyCount.intValue += 1
    }

    fun release(busy: Boolean = true) {
        // An unmatched release would drive the count negative and silently
        // disarm the lock for the rest of the session; fail loud instead.
        check(lockCount.intValue > 0 && (!busy || busyCount.intValue > 0)) {
            "NavigationLockState.release() called without a matching acquire()"
        }
        lockCount.intValue -= 1
        if (busy) busyCount.intValue -= 1
    }

    /** Closes every open modal window, for a deep link taking the user elsewhere. */
    fun dismissModals() {
        modalDismissSignal.intValue += 1
    }

    /**
     * [dismissModals], then waits for every modal window to leave, for a deep link to navigate
     * once none is left over the page it leaves: a sheet closes a frame or more after the request.
     */
    suspend fun dismissModalsAndAwait() {
        dismissModals()
        // ponytail: one that won't close (none should while nothing is busy) holds the deep link
        // only this long, then is left to close with its page.
        withTimeoutOrNull(MODAL_DISMISS_TIMEOUT_MILLIS) { snapshotFlow { isLocked }.first { !it } }
    }
}

private const val MODAL_DISMISS_TIMEOUT_MILLIS = 1_000L

// The default instance keeps previews, tests, and compositions outside the
// app shell (e.g. onboarding) working without an explicit provider.
val LocalNavigationLock = staticCompositionLocalOf { NavigationLockState() }

/**
 * Holds the navigation lock while [active] and this composable is composed. [busy] for a
 * mutation, which deep links wait for too; false for an open modal window, which they close.
 */
@Composable
fun NavigationLockEffect(active: Boolean, busy: Boolean = true) {
    val lock = LocalNavigationLock.current
    DisposableEffect(lock, active, busy) {
        if (active) {
            lock.acquire(busy)
        }
        onDispose {
            if (active) {
                lock.release(busy)
            }
        }
    }
}
