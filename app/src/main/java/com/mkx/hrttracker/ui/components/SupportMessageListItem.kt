package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SupportMessageListItem(
    text: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    painter: Painter? = null,
    showChevron: Boolean = false,
    index: Int = 0,
    count: Int = 1,
    trailingContent: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    supportingTextStyle: TextStyle? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIconSize: Dp = 20.dp,
    chevronTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    chevronSize: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        PreferenceSegmentedListItem(
            title = text,
            supportingText = supportingText,
            index = index,
            count = count,
            onClick = onClick,
            modifier = modifier.wrapContentHeight(),
            enabled = enabled,
            leadingContent = when {
                icon != null -> {
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = leadingIconTint,
                            modifier = Modifier.size(leadingIconSize)
                        )
                    }
                }

                painter != null -> {
                    {
                        Icon(
                            painter = painter,
                            contentDescription = null,
                            tint = leadingIconTint,
                            modifier = Modifier.size(leadingIconSize)
                        )
                    }
                }

                else -> null
            },
            trailingContent = trailingContent ?: if (showChevron) {
                {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = chevronTint,
                        modifier = Modifier.size(chevronSize)
                    )
                }
            } else {
                null
            },
            titleTextStyle = textStyle,
            supportingTextStyle = supportingTextStyle,
            titleColor = titleColor,
            containerColor = containerColor
        )
    }
}
