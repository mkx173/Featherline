package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

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
                    trailingIcon = item.trailingIcon,
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
