package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R

@Composable
fun DangerZoneListItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    index: Int? = null,
    count: Int? = null,
    supportText: String? = null,
) {
    val resolvedIconPainter = iconPainter ?: if (icon == null) {
        painterResource(R.drawable.ic_delete_forever)
    } else {
        null
    }

    PreferenceSegmentedListItem(
        title = label,
        index = index,
        count = count,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.72f),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        titleTextStyle = MaterialTheme.typography.titleMedium,
        titleColor = MaterialTheme.colorScheme.onSurface,
        leadingContent = {
            if (resolvedIconPainter != null) {
                Icon(
                    painter = resolvedIconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingText = supportText,
    )
}
