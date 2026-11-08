package com.mkx.hrttracker.ui.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
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

/**
 * Back on the in-window lock screen, while [enabled]: it does nothing, and nothing under it gets
 * it either, whenever it registered: not the shell's own handlers, such as an editor's or the
 * NavHost's, nor a hidden sheet's, which inherits a top-level page's dispatcher (TopLevelPager).
 * Every other one is on the default priority; this one is on the overlay's, ahead of them all.
 */
@Composable
internal fun AppLockBackHandler(enabled: Boolean) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    DisposableEffect(dispatcher, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler = object : NavigationEventHandler<NavigationEventInfo>(
            initialInfo = NavigationEventInfo.None,
            isBackEnabled = true,
        ) {
            override fun onBackCompleted() = Unit
        }
        dispatcher.addHandler(handler, NavigationEventDispatcher.PRIORITY_OVERLAY)
        onDispose { handler.remove() }
    }
}

/** Hide screen content: no screenshots, recordings or Recents thumbnail of this activity's windows. */
internal fun Activity.applyHideScreenContent(enabled: Boolean) {
    if (enabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setRecentsScreenshotEnabled(!enabled)
    }
}
