package com.mkx.hrttracker.util

import org.junit.Assert.assertThrows
import org.junit.Test

// No test calls ToastManager.init, so the object stays uninitialized in this JVM.
class ToastManagerTest {
    @Test
    fun bothEntryPoints_throwTheSameIllegalStateException_beforeInit() {
        assertThrows(IllegalStateException::class.java) { ToastManager.showMessage("x") }
        assertThrows(IllegalStateException::class.java) { ToastManager.showMessageRes(1) }
    }
}
