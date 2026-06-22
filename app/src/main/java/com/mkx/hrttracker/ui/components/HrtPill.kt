package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

/**
 * Size variants for [HrtPill]. Each variant owns a full set of layout tokens
 * (text style, icon size, padding, spacing) so the two looks can be tuned
 * independently from this one file.
 */
enum class HrtPillSize { XSmall, Small, Medium }

/**
 * Receiver scope for an [HrtPill] leading icon. Exposes [iconModifier], pre-sized
 * to the active [HrtPillSize], so call sites pick the icon type (painter, vector,
 * custom composable) while the size stays centralized here.
 */
@Stable
interface HrtPillScope {
    /** Apply to the leading [Icon] so its size follows the pill's size token. */
    val iconModifier: Modifier
}

private class HrtPillScopeImpl(override val iconModifier: Modifier) : HrtPillScope

private data class HrtPillTokens(
    val textStyle: TextStyle,
    val iconSize: Dp,
    val contentPadding: PaddingValues,
    val horizontalArrangement: Arrangement.Horizontal,
    val cjkTextOffsetAmount: Dp,
)

@Composable
private fun hrtPillTokens(size: HrtPillSize): HrtPillTokens {
    val typography = MaterialTheme.typography
    return when (size) {
        HrtPillSize.XSmall -> HrtPillTokens(
            textStyle = typography.labelSmall,
            iconSize = 12.dp,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            cjkTextOffsetAmount = (-0.5).dp
        )

        HrtPillSize.Small -> HrtPillTokens(
            textStyle = typography.labelSmall,
            iconSize = 12.dp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            cjkTextOffsetAmount = (-0.5).dp
        )

        HrtPillSize.Medium -> HrtPillTokens(
            textStyle = typography.labelMedium,
            iconSize = 14.dp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            cjkTextOffsetAmount = (-1).dp
        )
    }
}

/**
 * Base pill shell: a [CircleShape] [Surface] wrapping a centered [Row]. Use this
 * overload for pills whose content is more than a single icon + label (multiple
 * text lines, custom status glyphs, baseline-aligned rows).
 *
 * [contentPadding] defaults to the [size] token; override only for intentionally
 * tighter/looser pills (e.g. the compact calibration metadata pills).
 */
@Composable
fun HrtPill(
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = contentColorFor(containerColor),
    size: HrtPillSize = HrtPillSize.Medium,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = hrtPillTokens(size)
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(contentPadding ?: tokens.contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = tokens.horizontalArrangement,
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            content = row,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            content = row,
        )
    }
}

/**
 * Convenience pill for the dominant case: an optional leading [icon] followed by a
 * single [label]. Bakes in the shared label treatment (`cjkTextOffset`, the trailing
 * `2.dp` pad, single line) so it lives in exactly one place.
 *
 * The [icon] slot runs in an [HrtPillScope]; apply its `iconModifier` to size the
 * icon to the pill's [size] token. The icon inherits [contentColor] as its tint.
 */
@Composable
fun HrtPill(
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = contentColorFor(containerColor),
    // Overrides only the label's color (e.g. onPrimaryFixed for a colored pill) while the
    // icon keeps [contentColor]. Defaults to Unspecified so the label inherits [contentColor].
    labelColor: Color = Color.Unspecified,
    size: HrtPillSize = HrtPillSize.Medium,
    fontWeight: FontWeight? = null,
    icon: (@Composable HrtPillScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val tokens = hrtPillTokens(size)
    HrtPill(
        containerColor = containerColor,
        modifier = modifier,
        contentColor = contentColor,
        size = size,
        onClick = onClick,
    ) {
        if (icon != null) {
            HrtPillScopeImpl(Modifier.size(tokens.iconSize)).icon()
        }
        Text(
            text = label,
            style = tokens.textStyle,
            color = labelColor,
            fontWeight = fontWeight,
            maxLines = 1,
            // The trailing pad only balances a leading icon's optical weight; a
            // text-only pill stays centered without it.
            modifier = Modifier
                .then(if (icon != null) Modifier.padding(end = 2.dp) else Modifier)
                .cjkTextOffset(label, amount = tokens.cjkTextOffsetAmount),
        )
    }
}

@Preview(showBackground = true, widthDp = 280)
@Composable
private fun HrtPillGalleryPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HrtPill(
                label = "Medium · label",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                size = HrtPillSize.Medium,
                icon = { Icon(painterResource(R.drawable.ic_labs), null, iconModifier) },
            )
            HrtPill(
                label = "Small · semibold",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                size = HrtPillSize.Small,
                fontWeight = FontWeight.SemiBold,
                icon = { Icon(painterResource(R.drawable.ic_schedule), null, iconModifier) },
            )
            HrtPill(
                label = "Small · no icon",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                size = HrtPillSize.Small,
                fontWeight = FontWeight.SemiBold,
            )
            HrtPill(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                size = HrtPillSize.Small,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "Base · compact",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
