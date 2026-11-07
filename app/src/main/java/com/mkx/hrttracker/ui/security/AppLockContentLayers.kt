package com.mkx.hrttracker.ui.security

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.mkx.hrttracker.ui.components.LocalAppLocked

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

/**
 * The home shell, under the in-window lock screen while [locked]: hidden from accessibility, and
 * keys or a D-pad can neither move focus into it nor type into a field focused before the lock,
 * as with a top-level page slid away (TopLevelPager). Its sheets, dialogs and menus hide too.
 */
@Composable
internal fun HiddenUnderAppLock(
    locked: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(locked) {
        if (locked && hasFocus) focusManager.clearFocus(force = true)
    }
    Box(
        modifier
            .then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)
            .onFocusChanged { state -> hasFocus = state.hasFocus }
            .focusProperties { onEnter = { if (locked) cancelFocusChange() } }
            .focusGroup(),
    ) {
        CompositionLocalProvider(LocalAppLocked provides locked, content = content)
    }
}
