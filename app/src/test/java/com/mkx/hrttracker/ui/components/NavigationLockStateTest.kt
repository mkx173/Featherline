package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertFalse
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
}
