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
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
