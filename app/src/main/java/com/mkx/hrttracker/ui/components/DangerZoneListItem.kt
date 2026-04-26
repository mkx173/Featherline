package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.util.rememberAppLocale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DangerZoneListItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Delete,
    index: Int = 0,
    count: Int = 1
) {
    val appLocale = rememberAppLocale()
    val isChinese = appLocale.language == "zh"

    SegmentedListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
            )
        },
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shapes = segmentedListItemShapes(index = index, count = count)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.graphicsLayer {
                translationY = if (isChinese) (-1).dp.toPx() else 0f
            }
        )
    }
}
