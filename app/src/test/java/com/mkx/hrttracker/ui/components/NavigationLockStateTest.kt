package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLockStateTest {

    @Test
    fun unlocked_byDefault() {
        assertFalse(NavigationLockState().isLocked)
    }

    @Test
    fun locked_whileAnyHolderRemains() {
        // Multiple holders can overlap (a dialog above a sheet, a save
        // finishing while a sheet hides); the chrome must stay locked until
        // every holder has released.
        val lock = NavigationLockState()
        lock.acquire()
        lock.acquire()
        lock.release()
        assertTrue(lock.isLocked)
        lock.release()
        assertFalse(lock.isLocked)
    }

    @Test
    fun unmatchedRelease_failsLoud() {
        // A pairing bug that over-releases would drive the count negative and
        // silently disarm the lock for the rest of the session — the next
        // acquire would leave the count at zero and the chrome tappable
        // during a confirmed write. Failing loud surfaces the pairing bug.
        val lock = NavigationLockState()
        assertThrows(IllegalStateException::class.java) { lock.release() }
        lock.acquire()
        lock.release()
        assertThrows(IllegalStateException::class.java) { lock.release() }
    }
}
