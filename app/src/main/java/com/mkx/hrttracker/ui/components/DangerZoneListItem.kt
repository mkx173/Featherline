package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun DangerZoneListItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.DeleteForever,
    iconPainter: Painter? = null,
    index: Int = 0,
    count: Int = 1,
    supportText: String? = null,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.38f)
    }

    PreferenceSegmentedListItem(
        title = label,
        index = index,
        count = count,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.72f),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        titleTextStyle = MaterialTheme.typography.titleMedium,
        titleColor = contentColor,
        leadingContent = {
            if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor,
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = contentColor,
            )
        },
        supportingText = supportText,
    )
}
