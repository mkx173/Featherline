package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorSegmentedListItem(
    index: Int? = null,
    count: Int? = null,
    modifier: Modifier = Modifier,
    // Null onClick renders a non-clickable static card with the same segmented
    // shape and container color — used for purely informational rows that
    // shouldn't show a ripple. Non-null delegates to the standard
    // SegmentedListItem, which is always clickable and rippled.
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledContainerColor: Color = containerColor,
    cornerShape: CornerBasedShape = MaterialTheme.shapes.large,
    pressedShape: Shape? = null,
    // Detaches the row into a standalone card with all four corners rounded to
    // [cornerShape], ignoring its segment position. Toggling it animates the
    // corners (via segmentedListItemShapes' per-corner springs) between grouped
    // and detached -- e.g. an editable list that separates its rows in edit mode.
    fullyRounded: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val position = currentSegmentPosition(explicitSegmentPosition(index, count))
    val shapes = segmentedListItemShapes(
        index = position.index,
        count = position.count,
        cornerShape = cornerShape,
        pressedShape = pressedShape,
        fullyRounded = fullyRounded,
    )
    // On a haze bottom sheet the row keeps its container color as the tint of a
    // thick blur (clipped to the resting segmented shape) and draws its actual
    // container transparent so the blur shows through.
    val hazeSheet = hazeSheetBlurActive()
    val taggedModifier = modifier
        .then(
            if (hazeSheet) {
                Modifier.hazeSheetSurface(containerColor = containerColor, shape = shapes.shape)
            } else {
                Modifier
            }
        )
        .segmentPositionSemantics(position)
    val resolvedContainerColor = if (hazeSheet) Color.Transparent else containerColor
    val resolvedDisabledContainerColor = if (hazeSheet) Color.Transparent else disabledContainerColor

    // A null onClick is a static, non-interactive row. Route it through the same
    // SegmentedListItem as clickable rows — but disabled, so it never ripples —
    // with its disabled colors pinned to the enabled defaults so it keeps
    // full-strength colors. Using one composable for both keeps static and
    // clickable rows the same height and avoids resetting animated slot content
    // (e.g. a fading trailing icon) when a row toggles between the two.
    val isClickable = onClick != null
    val colors = if (isClickable) {
        ListItemDefaults.colors(
            containerColor = resolvedContainerColor,
            disabledContainerColor = resolvedDisabledContainerColor,
        )
    } else {
        ListItemDefaults.colors(
            containerColor = resolvedContainerColor,
            disabledContainerColor = resolvedContainerColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
            disabledLeadingContentColor = MaterialTheme.colorScheme.onSurface,
            disabledTrailingContentColor = MaterialTheme.colorScheme.onSurface,
            disabledOverlineContentColor = MaterialTheme.colorScheme.onSurface,
            disabledSupportingContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    SegmentedListItem(
        modifier = taggedModifier,
        enabled = isClickable && enabled,
        onClick = onClick ?: {},
        onLongClick = onLongClick,
        colors = colors,
        shapes = shapes,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supportingContent,
        overlineContent = overlineContent,
        content = content,
    )
}
