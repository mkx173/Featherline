package com.mkx.hrttracker.ui.security

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockContentLayersTest {
    @Test
    fun notReady_showsNoContent() {
        assertEquals(
            AppLockContentLayers(),
            appLockContentLayers(
                appLockReady = false,
                shouldShowLockScreen = false,
                homeShellReady = false,
            )
        )
    }

    @Test
    fun lockedBeforeHomeShell_showsInWindowLockOnly() {
        assertEquals(
            AppLockContentLayers(
                showInWindowLockScreen = true,
            ),
            appLockContentLayers(
                appLockReady = true,
                shouldShowLockScreen = true,
                homeShellReady = false,
            )
        )
    }

    @Test
    fun lockedHomeShell_keepsInWindowLockLayerOverHome() {
        assertEquals(
            AppLockContentLayers(
                showHomeShell = true,
                showInWindowLockScreen = true,
            ),
            appLockContentLayers(
                appLockReady = true,
                shouldShowLockScreen = true,
                homeShellReady = true,
            )
        )
    }

    @Test
    fun unlockedHomeShell_showsHomeOnly() {
        assertEquals(
            AppLockContentLayers(
                showHomeShell = true,
            ),
            appLockContentLayers(
                appLockReady = true,
                shouldShowLockScreen = false,
                homeShellReady = true,
            )
        )
    }
}
