package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HrtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    iconSpacing: Dp = 8.dp,
    iconContentDescription: String? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    compact: Boolean = false,
    contentPadding: PaddingValues? = null,
) {
    val resolvedContentPadding = contentPadding
        ?: ButtonDefaults.contentPaddingFor(
            buttonHeight = ButtonDefaults.MinHeight,
            hasStartIcon = icon != null,
        )

    ButtonContainer(compact = compact) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            contentPadding = resolvedContentPadding,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = iconModifier,
                )
                Spacer(modifier = Modifier.size(iconSpacing))
            }
            LocalizedButtonLabelText(text = text)
        }
    }
}

@Composable
fun HrtFilledTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    iconSpacing: Dp = 8.dp,
    iconContentDescription: String? = null,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    compact: Boolean = false,
    contentPadding: PaddingValues? = null,
) {
    val resolvedContentPadding = contentPadding
        ?: ButtonDefaults.contentPaddingFor(
            buttonHeight = ButtonDefaults.MinHeight,
            hasStartIcon = icon != null,
        )

    ButtonContainer(compact = compact) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            contentPadding = resolvedContentPadding,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = iconModifier,
                )
                Spacer(modifier = Modifier.size(iconSpacing))
            }
            LocalizedButtonLabelText(text = text)
        }
    }
}

@Composable
fun HrtOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    iconSpacing: Dp = 8.dp,
    iconContentDescription: String? = null,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
    compact: Boolean = false,
    contentPadding: PaddingValues? = null,
) {
    val resolvedContentPadding = contentPadding
        ?: ButtonDefaults.contentPaddingFor(
            buttonHeight = ButtonDefaults.MinHeight,
            hasStartIcon = icon != null,
        )

    ButtonContainer(compact = compact) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            border = border,
            contentPadding = resolvedContentPadding,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = iconModifier,
                )
                Spacer(modifier = Modifier.size(iconSpacing))
            }
            LocalizedButtonLabelText(text = text)
        }
    }
}

@Composable
private fun ButtonContainer(
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    if (compact) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            content = content,
        )
    } else {
        content()
    }
}

@Composable
internal fun LocalizedButtonLabelText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    applyCjkTextOffset: Boolean = true,
) {
    Text(
        text = text,
        modifier = modifier.cjkTextOffset(text = text, enabled = applyCjkTextOffset),
        style = textStyle ?: LocalTextStyle.current
    )
}
