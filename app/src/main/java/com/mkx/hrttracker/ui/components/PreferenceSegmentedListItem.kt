package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceSegmentedListItem(
    title: String,
    index: Int,
    count: Int,
    // Null onClick renders a non-clickable static row (no ripple), matching
    // the static path EditorSegmentedListItem supports.
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledContainerColor: Color = containerColor,
    supportingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    titleTextStyle: TextStyle? = null,
    supportingTextStyle: TextStyle? = null,
    titleColor: Color? = null,
    supportingTextColor: Color? = null,
    titleCjkTextOffsetEnabled: Boolean = true,
    supportingCjkTextOffsetEnabled: Boolean = true,
) {
    val resolvedTitleTextStyle = titleTextStyle ?: MaterialTheme.typography.titleMedium
    val resolvedSupportingTextStyle = supportingTextStyle ?: MaterialTheme.typography.bodyMedium
    val resolvedTitleColor = titleColor ?: Color.Unspecified
    val resolvedSupportingTextColor =
        supportingTextColor ?: MaterialTheme.colorScheme.onSurfaceVariant

    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        containerColor = containerColor,
        disabledContainerColor = disabledContainerColor,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = resolvedTitleTextStyle,
                    color = resolvedTitleColor,
                    modifier = Modifier.cjkTextOffset(
                        text = title,
                        enabled = titleCjkTextOffsetEnabled,
                    ),
                    fontWeight = FontWeight.Normal
                )
                supportingText?.let { supporting ->
                    Text(
                        text = supporting,
                        style = resolvedSupportingTextStyle,
                        color = resolvedSupportingTextColor,
                        modifier = Modifier.cjkTextOffset(
                            text = supporting,
                            enabled = supportingCjkTextOffsetEnabled,
                        )
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}
