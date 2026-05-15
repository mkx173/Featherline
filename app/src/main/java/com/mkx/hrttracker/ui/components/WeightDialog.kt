package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeightDialog(
    profile: UserProfile,
    onSave: (Double, WeightUnit) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isInProgress: Boolean = false,
) {
    var valueText by remember {
        mutableStateOf(profile.weightOriginalValue?.let { formatWeightForInput(it) }.orEmpty())
    }
    var selectedUnit by remember { mutableStateOf(profile.weightOriginalUnit) }
    var showValidationError by remember { mutableStateOf(false) }
    val submit = {
        if (!isInProgress) {
            val parsed = valueText.trim().replace(',', '.').toDoubleOrNull()
            if (parsed == null || parsed <= 0.0) {
                showValidationError = true
            } else {
                onSave(parsed, selectedUnit)
            }
        }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!isInProgress) onDismiss() },
        title = { Text(text = stringResource(R.string.personalization_weight_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.padding_small)
                )
            ) {
                Text(
                    text = stringResource(R.string.personalization_weight_dialog_description)
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = valueText,
                    onValueChange = {
                        valueText = it
                        showValidationError = false
                    },
                    label = {
                        Text(text = stringResource(R.string.personalization_weight_dialog_value_label))
                    },
                    enabled = !isInProgress,
                    suffix = {
                        Text(text = stringResource(selectedUnit.shortLabelRes))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = showValidationError,
                    supportingText = if (showValidationError) {
                        {
                            Text(
                                text = stringResource(R.string.personalization_weight_validation_invalid)
                            )
                        }
                    } else null
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ConnectedButtonGroup(
                        options = WeightUnit.entries,
                        selectedOption = selectedUnit,
                        optionLabel = { unit -> stringResource(unit.shortLabelRes) },
                        onOptionSelected = { selectedUnit = it },
                        enabled = !isInProgress,
                        layout = ConnectedButtonGroupLayout.ROW,
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        applyCjkTextOffset = false
                    )
                }

            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (profile.weightOriginalValue != null) {
                    TextButton(
                        enabled = !isInProgress,
                        onClick = onClear
                    ) {
                        Text(text = stringResource(R.string.personalization_weight_dialog_clear))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    enabled = !isInProgress,
                    onClick = onDismiss,
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
                TextButton(
                    enabled = !isInProgress,
                    onClick = { submit() },
                ) {
                    Text(text = stringResource(R.string.personalization_weight_dialog_save))
                }
            }
        },
    )
}

private fun formatWeightForInput(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
