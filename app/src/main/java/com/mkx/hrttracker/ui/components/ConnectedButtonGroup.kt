package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

enum class ConnectedButtonGroupLayout {
    FLOW_ROW,
    ROW,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedButtonGroup(
    modifier: Modifier = Modifier,
    options: List<T>,
    selectedOption: T?,
    optionLabel: @Composable (T) -> String,
    optionIcons: ((T) -> List<ImageVector>)? = null,
    optionLeadingContent: (@Composable (T) -> Unit)? = null,
    onOptionSelected: (T) -> Unit,
    enabled: Boolean = true,
    colors: ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors(),
    layout: ConnectedButtonGroupLayout = ConnectedButtonGroupLayout.FLOW_ROW,
    expandOptions: Boolean = false,
    applyCjkTextOffset: Boolean = true,
    textStyle: TextStyle? = null,
) {
    val resolvedSelectedOption = resolveConnectedButtonSelection(options, selectedOption)
    ConnectedButtonGroup(
        modifier = modifier,
        options = options,
        isOptionSelected = { option -> option == resolvedSelectedOption },
        optionLabel = optionLabel,
        optionIcons = optionIcons,
        optionLeadingContent = optionLeadingContent,
        onOptionToggled = { option ->
            if (option != resolvedSelectedOption) {
                onOptionSelected(option)
            }
        },
        enabled = enabled,
        applyCjkTextOffset = applyCjkTextOffset,
        colors = colors,
        layout = layout,
        expandOptions = expandOptions,
        textStyle = textStyle,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedButtonGroup(
    modifier: Modifier = Modifier,
    options: List<T>,
    selectedOptions: Set<T>,
    optionLabel: @Composable (T) -> String,
    optionIcons: ((T) -> List<ImageVector>)? = null,
    optionLeadingContent: (@Composable (T) -> Unit)? = null,
    onOptionToggled: (T) -> Unit,
    enabled: Boolean = true,
    colors: ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors(),
    layout: ConnectedButtonGroupLayout = ConnectedButtonGroupLayout.FLOW_ROW,
    expandOptions: Boolean = false,
    applyCjkTextOffset: Boolean = true,
    textStyle: TextStyle? = null,
) {
    ConnectedButtonGroup(
        modifier = modifier,
        options = options,
        isOptionSelected = { option -> option in selectedOptions },
        optionLabel = optionLabel,
        optionIcons = optionIcons,
        optionLeadingContent = optionLeadingContent,
        onOptionToggled = onOptionToggled,
        enabled = enabled,
        applyCjkTextOffset = applyCjkTextOffset,
        colors = colors,
        layout = layout,
        expandOptions = expandOptions,
        textStyle = textStyle,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedButtonGroup(
    modifier: Modifier = Modifier,
    options: List<T>,
    isOptionSelected: (T) -> Boolean,
    optionLabel: @Composable (T) -> String,
    optionIcons: ((T) -> List<ImageVector>)? = null,
    optionLeadingContent: (@Composable (T) -> Unit)? = null,
    onOptionToggled: (T) -> Unit,
    enabled: Boolean = true,
    colors: ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors(),
    layout: ConnectedButtonGroupLayout = ConnectedButtonGroupLayout.FLOW_ROW,
    expandOptions: Boolean = false,
    applyCjkTextOffset: Boolean = true,
    textStyle: TextStyle? = null,
) {
    val horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    val verticalArrangement = Arrangement.spacedBy(2.dp)

    when (layout) {
        ConnectedButtonGroupLayout.FLOW_ROW -> {
            FlowRow(
                modifier = modifier,
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = verticalArrangement,
            ) {
                options.forEachIndexed { index, option ->
                    ConnectedButtonGroupButton(
                        option = option,
                        index = index,
                        optionCount = options.size,
                        selected = isOptionSelected(option),
                        optionLabel = optionLabel,
                        optionIcons = optionIcons?.invoke(option).orEmpty(),
                        optionLeadingContent = optionLeadingContent?.let { leadingContent ->
                            { leadingContent(option) }
                        },
                        onOptionToggled = onOptionToggled,
                        enabled = enabled,
                        applyCjkTextOffset = applyCjkTextOffset,
                        colors = colors,
                        textStyle = textStyle,
                    )
                }
            }
        }

        ConnectedButtonGroupLayout.ROW -> {
            val rowModifier = if (expandOptions) {
                modifier
            } else {
                modifier.horizontalScroll(rememberScrollState())
            }
            Row(
                modifier = rowModifier,
                horizontalArrangement = horizontalArrangement,
            ) {
                options.forEachIndexed { index, option ->
                    val buttonModifier = if (expandOptions) {
                        Modifier.weight(1f)
                    } else {
                        Modifier
                    }
                    ConnectedButtonGroupRowButton(
                        modifier = buttonModifier,
                        option = option,
                        index = index,
                        optionCount = options.size,
                        selected = isOptionSelected(option),
                        optionLabel = optionLabel,
                        optionIcons = optionIcons?.invoke(option).orEmpty(),
                        optionLeadingContent = optionLeadingContent?.let { leadingContent ->
                            { leadingContent(option) }
                        },
                        onOptionToggled = onOptionToggled,
                        enabled = enabled,
                        applyCjkTextOffset = applyCjkTextOffset,
                        colors = colors,
                        textStyle = textStyle,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedButtonGroupButton(
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    option: T,
    index: Int,
    optionCount: Int,
    selected: Boolean,
    optionLabel: @Composable (T) -> String,
    optionIcons: List<ImageVector>,
    optionLeadingContent: (@Composable () -> Unit)?,
    onOptionToggled: (T) -> Unit,
    enabled: Boolean,
    applyCjkTextOffset: Boolean,
    colors: ToggleButtonColors,
) {
    ToggleButton(
        modifier = modifier,
        checked = selected,
        onCheckedChange = { onOptionToggled(option) },
        enabled = enabled || selected,
        colors = colors,
        shapes =
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                optionCount - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
    ) {
        optionLeadingContent?.invoke()
        if (optionLeadingContent != null) {
            Spacer(modifier = Modifier.size(ToggleButtonDefaults.IconSpacing))
        } else {
            optionIcons.forEach { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.size(ToggleButtonDefaults.IconSpacing))
            }
        }
        LocalizedButtonLabelText(
            text = optionLabel(option),
            applyCjkTextOffset = applyCjkTextOffset,
            textStyle = textStyle
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedButtonGroupRowButton(
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    option: T,
    index: Int,
    optionCount: Int,
    selected: Boolean,
    optionLabel: @Composable (T) -> String,
    optionIcons: List<ImageVector>,
    optionLeadingContent: (@Composable () -> Unit)?,
    onOptionToggled: (T) -> Unit,
    enabled: Boolean,
    applyCjkTextOffset: Boolean,
    colors: ToggleButtonColors,
) {
    ConnectedButtonGroupButton(
        modifier = modifier,
        textStyle = textStyle,
        option = option,
        index = index,
        optionCount = optionCount,
        selected = selected,
        optionLabel = optionLabel,
        optionIcons = optionIcons,
        optionLeadingContent = optionLeadingContent,
        onOptionToggled = onOptionToggled,
        enabled = enabled,
        applyCjkTextOffset = applyCjkTextOffset,
        colors = colors,
    )
}

internal fun <T> resolveConnectedButtonSelection(options: List<T>, selectedOption: T?): T? {
    return options.firstOrNull { it == selectedOption } ?: options.firstOrNull()
}
