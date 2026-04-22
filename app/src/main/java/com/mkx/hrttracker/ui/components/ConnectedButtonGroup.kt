package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onOptionSelected: (T) -> Unit,
    enabled: Boolean = true,
    layout: ConnectedButtonGroupLayout = ConnectedButtonGroupLayout.FLOW_ROW,
    expandOptions: Boolean = false,
) {
    val resolvedSelectedOption = resolveConnectedButtonSelection(options, selectedOption)
    ConnectedButtonGroup(
        modifier = modifier,
        options = options,
        isOptionSelected = { option -> option == resolvedSelectedOption },
        optionLabel = optionLabel,
        onOptionToggled = { option ->
            if (option != resolvedSelectedOption) {
                onOptionSelected(option)
            }
        },
        enabled = enabled,
        layout = layout,
        expandOptions = expandOptions,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedButtonGroup(
    modifier: Modifier = Modifier,
    options: List<T>,
    selectedOptions: Set<T>,
    optionLabel: @Composable (T) -> String,
    onOptionToggled: (T) -> Unit,
    enabled: Boolean = true,
    layout: ConnectedButtonGroupLayout = ConnectedButtonGroupLayout.FLOW_ROW,
    expandOptions: Boolean = false,
) {
    ConnectedButtonGroup(
        modifier = modifier,
        options = options,
        isOptionSelected = { option -> option in selectedOptions },
        optionLabel = optionLabel,
        onOptionToggled = onOptionToggled,
        enabled = enabled,
        layout = layout,
        expandOptions = expandOptions,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedButtonGroup(
    modifier: Modifier = Modifier,
    options: List<T>,
    isOptionSelected: (T) -> Boolean,
    optionLabel: @Composable (T) -> String,
    onOptionToggled: (T) -> Unit,
    enabled: Boolean = true,
    layout: ConnectedButtonGroupLayout = ConnectedButtonGroupLayout.FLOW_ROW,
    expandOptions: Boolean = false,
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
                        onOptionToggled = onOptionToggled,
                        enabled = enabled,
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
                        onOptionToggled = onOptionToggled,
                        enabled = enabled,
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
    option: T,
    index: Int,
    optionCount: Int,
    selected: Boolean,
    optionLabel: @Composable (T) -> String,
    onOptionToggled: (T) -> Unit,
    enabled: Boolean,
) {
    ToggleButton(
        modifier = modifier,
        checked = selected,
        onCheckedChange = { onOptionToggled(option) },
        enabled = enabled,
        shapes =
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                optionCount - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
    ) {
        Text(optionLabel(option))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedButtonGroupRowButton(
    modifier: Modifier = Modifier,
    option: T,
    index: Int,
    optionCount: Int,
    selected: Boolean,
    optionLabel: @Composable (T) -> String,
    onOptionToggled: (T) -> Unit,
    enabled: Boolean,
) {
    ConnectedButtonGroupButton(
        modifier = modifier,
        option = option,
        index = index,
        optionCount = optionCount,
        selected = selected,
        optionLabel = optionLabel,
        onOptionToggled = onOptionToggled,
        enabled = enabled,
    )
}

internal fun <T> resolveConnectedButtonSelection(options: List<T>, selectedOption: T?): T? {
    return options.firstOrNull { it == selectedOption } ?: options.firstOrNull()
}
