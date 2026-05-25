package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorSegmentedListItem(
    index: Int,
    count: Int,
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
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Surface(
            modifier = modifier,
            color = containerColor,
            shape = segmentedListItemShape(
                index = index,
                count = count,
                cornerShape = cornerShape,
            ),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                supportingContent = supportingContent,
                overlineContent = overlineContent,
                headlineContent = content,
            )
        }
        return
    }
    SegmentedListItem(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            disabledContainerColor = disabledContainerColor,
        ),
        shapes = segmentedListItemShapes(
            index = index,
            count = count,
            cornerShape = cornerShape,
            pressedShape = pressedShape,
        ),
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supportingContent,
        overlineContent = overlineContent,
        content = content,
    )
}
