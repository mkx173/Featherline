package com.mkx.hrttracker.ui.components

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun modalWindow_locksWithoutBeingBusy() {
        // Deep links wait only for a write in flight; an open sheet or dialog is closed instead.
        val lock = NavigationLockState()
        lock.acquire(busy = false)
        assertTrue(lock.isLocked)
        assertFalse(lock.isBusy)
        lock.acquire()
        assertTrue(lock.isBusy)
        lock.release()
        assertFalse(lock.isBusy)
        lock.release(busy = false)
        assertFalse(lock.isLocked)
    }

    @Test
    fun dismissModals_bumpsTheRequest() {
        val lock = NavigationLockState()
        val before = lock.modalDismissRequest
        lock.dismissModals()
        assertTrue(lock.modalDismissRequest != before)
    }

    @Test
    fun dismissModalsAndAwait_returnsOnceTheModalsClose() = runTest {
        // A deep link navigates only once the sheet over the page it leaves is gone.
        val lock = NavigationLockState()
        lock.acquire(busy = false)
        val before = lock.modalDismissRequest
        val wait = launch { lock.dismissModalsAndAwait() }
        runCurrent()
        assertTrue(lock.modalDismissRequest != before)
        assertFalse(wait.isCompleted)
        lock.release(busy = false)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(wait.isCompleted)
    }

    @Test
    fun dismissModalsAndAwait_givesUpOnAModalThatStays() = runTest {
        val lock = NavigationLockState()
        lock.acquire(busy = false)
        val wait = launch { lock.dismissModalsAndAwait() }
        advanceTimeBy(1_001)
        assertTrue(wait.isCompleted)
    }
}
