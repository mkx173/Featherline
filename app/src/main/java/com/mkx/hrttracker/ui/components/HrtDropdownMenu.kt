package com.mkx.hrttracker.ui.components

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
import androidx.compose.ui.window.PopupPositionProvider

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
 * them as it closes (see [pagerMenuPopupProperties]), and it closes with a page sliding away.
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
    DropdownMenuPopup(
        // Closed at once, even while the caller hasn't caught up.
        expanded = expanded && shown,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = pagerMenuPopupProperties(exemption),
    ) {
        CountOpenMenu(exemption)
        content()
    }
}

/**
 * [TooltipBox] for the app's tooltips. Its popup is a window of its own, which a persistent one
 * keeps up until dismissed, over the lock screen too: under the app lock, it's gone at once, as a
 * menu's is ([HrtDropdownMenuPopup]), so nothing in it, such as a colour picker, takes a tap there,
 * and it's open again after.
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
    TooltipBox(positionProvider = positionProvider, tooltip = tooltip, state = shownState, content = content)
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
