package com.mkx.hrttracker.ui.security

internal data class AppLockContentLayers(
    val showHomeShell: Boolean = false,
    val showInWindowLockScreen: Boolean = false,
)

internal fun appLockContentLayers(
    appLockReady: Boolean,
    shouldShowLockScreen: Boolean,
    homeShellReady: Boolean,
): AppLockContentLayers {
    if (!appLockReady) {
        return AppLockContentLayers()
    }

    if (!homeShellReady) {
        return AppLockContentLayers(
            showInWindowLockScreen = shouldShowLockScreen,
        )
    }

    return AppLockContentLayers(
        showHomeShell = true,
        showInWindowLockScreen = shouldShowLockScreen,
    )
}
