package com.mkx.hrttracker.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

data class HrtDropdownMenuItem(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val supportingText: String? = null,
    val trailingIcon: (@Composable () -> Unit)? = null,
)

/**
 * Anchor position for [HrtDropdownMenu]. Wraps the experimental
 * [MenuAnchorPosition] so callers don't need to opt in.
 */
sealed class HrtDropdownAnchor {
    /** Menu's start edge aligns with the anchor's start edge (Material default). */
    object Below : HrtDropdownAnchor()

    /** Menu's end edge aligns with the anchor's end edge. */
    object EndAlignedBelow : HrtDropdownAnchor()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HrtDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<HrtDropdownMenuItem>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuGroupContentPadding,
    // Invoked once the popup has fully dismissed (after its exit animation), when the
    // menu content actually leaves the composition. Callers that must avoid disrupting
    // the UI while the menu is still animating out — e.g. a language switch that
    // re-localizes the whole screen — can defer that work until here.
    onExitFinished: () -> Unit = {},
) {
    if (items.isEmpty()) {
        return
    }
    val scrollState = rememberScrollState()
    val currentOnExitFinished by rememberUpdatedState(onExitFinished)

    HrtDropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        DisposableEffect(Unit) {
            onDispose { currentOnExitFinished() }
        }
        DropdownMenuGroup(
            modifier = Modifier.verticalScroll(scrollState),
            shapes = MenuDefaults.groupShapes(),
            contentPadding = contentPadding,
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    onClick = {
                        onDismissRequest()
                        item.onClick()
                    },
                    text = { Text(text = item.text) },
                    shape = expressiveMenuItemShape(index = index, count = items.size),
                    enabled = item.enabled,
                    supportingText = item.supportingText?.let { supportingText ->
                        {
                            Text(
                                text = supportingText,
                            )
                        }
                    },
                    trailingContent = item.trailingIcon,
                )
            }
        }
    }
}

/**
 * [DropdownMenuPopup] for the app's menus: over the top-level pages, a swipe outside it turns
 * them as it closes (see [pagerMenuPopupProperties]), and it closes with a page sliding away. A
 * back swipe closes it with its Back, see [KeepOpenOnBackSwipes].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HrtDropdownMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val exemption = LocalPagerSwipeExemption.current
    val shown = LocalModalHostShown.current
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    if (expanded && !shown) LaunchedEffect(Unit) { currentOnDismissRequest() }
    // Above the app lock: gone at once, rather than animating out over it, and open again after.
    if (LocalAppLocked.current) return
    val screen = backSwipeScreen()
    DropdownMenuPopup(
        // Closed at once, even while the caller hasn't caught up.
        expanded = expanded && shown,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = pagerMenuPopupProperties(exemption),
    ) {
        CountOpenMenu(exemption)
        KeepOpenOnBackSwipes(screen)
        content()
    }
}

/**
 * [TooltipBox] for the app's tooltips. Its popup is a window of its own, which a persistent one
 * keeps up until dismissed, over the lock screen too: under the app lock, it's gone at once, as a
 * menu's is ([HrtDropdownMenuPopup]), so nothing in it, such as a colour picker, takes a tap there,
 * and it's open again after. A back swipe leaves it to the screen's Back, see [KeepOpenOnBackSwipes].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrtTooltipBox(
    positionProvider: PopupPositionProvider,
    tooltip: @Composable TooltipScope.() -> Unit,
    state: TooltipState,
    content: @Composable () -> Unit,
) {
    val locked by rememberUpdatedState(LocalAppLocked.current)
    // TooltipBox shows the popup while its state is visible.
    val shownState = remember(state) {
        object : TooltipState by state {
            override val isVisible: Boolean get() = state.isVisible && !locked
        }
    }
    val screen = backSwipeScreen()
    TooltipBox(
        positionProvider = positionProvider,
        tooltip = {
            KeepOpenOnBackSwipes(screen)
            tooltip()
        },
        state = shownState,
        content = content,
    )
}

/**
 * Keeps the popup this is called in open on a touch outside it that starts where a back swipe
 * does: at a side of [screen], within its system gesture insets. Closed on that touch down, the
 * popup's window would go mid-swipe and take the swipe's Back astray: on Android 14 a menu's goes
 * on to the window beneath, which navigates, and a tooltip's is lost. The swipe's Back closes it
 * instead, as the Back key does.
 */
// ponytail: a tap within a side's gesture inset no longer closes the popup either, and over the
// pages a vertical drag starting there scrolls the page under the open menu. Telling either from a
// swipe takes the touch's moves or release, which a popup over the pages, told only of the touch
// down, never sees.
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun KeepOpenOnBackSwipes(screen: View) {
    val popup = LocalView.current.rootView
    DisposableEffect(popup, screen) {
        // Ahead of the popup's own dismissal, on touch down outside it: taken, it never sees it.
        popup.setOnTouchListener { _, event ->
            val outside = event.actionMasked == MotionEvent.ACTION_OUTSIDE ||
                event.actionMasked == MotionEvent.ACTION_DOWN &&
                (event.x < 0 || event.x >= popup.width || event.y < 0 || event.y >= popup.height)
            outside && screen.isOnBackSwipeEdge(event.rawX)
        }
        onDispose { popup.setOnTouchListener(null) }
    }
}

/**
 * The root of the activity's full-screen window, whose sides are the screen's. A dialog's window
 * may not reach them, and its insets only cover where it does.
 */
@Composable
private fun backSwipeScreen(): View = LocalActivity.current?.window?.decorView ?: LocalView.current.rootView

/**
 * Whether a back swipe starts at [rawX] on the screen, at a side of this window root: within its
 * system gesture insets, as the window has them before any view takes them.
 */
private fun View.isOnBackSwipeEdge(rawX: Float): Boolean {
    // No back swipe before Android 10, where these insets fall back to the system bars'.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val edges = ViewCompat.getRootWindowInsets(this)?.getInsets(WindowInsetsCompat.Type.systemGestures())
        ?: return false
    val x = rawX - IntArray(2).also(this::getLocationOnScreen)[0]
    return x >= 0 && x < edges.left || x >= width - edges.right && x < width
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun expressiveMenuItemShape(
    index: Int,
    count: Int,
): Shape {
    return when {
        count <= 1 -> MaterialTheme.shapes.medium
        index == 0 -> MenuDefaults.leadingItemShape
        index == count - 1 -> MenuDefaults.trailingItemShape
        else -> MenuDefaults.middleItemShape
    }
}
