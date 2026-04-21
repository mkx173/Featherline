package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightDialog(
    profile: UserProfile,
    onSave: (Double, WeightUnit) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var valueText by remember {
        mutableStateOf(profile.weightOriginalValue?.let { formatWeightForInput(it) }.orEmpty())
    }
    var selectedUnit by remember { mutableStateOf(profile.weightOriginalUnit) }
    var showValidationError by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = showValidationError,
                    supportingText = if (showValidationError) {
                        {
                            Text(
                                text = stringResource(R.string.personalization_weight_validation_invalid)
                            )
                        }
                    } else null
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val entries = WeightUnit.entries
                    entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = entries.size
                            ),
                            label = { Text(text = stringResource(unit.shortLabelRes)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = valueText.trim().replace(',', '.').toDoubleOrNull()
                if (parsed == null || parsed <= 0.0) {
                    showValidationError = true
                } else {
                    onSave(parsed, selectedUnit)
                }
            }) {
                Text(text = stringResource(R.string.personalization_weight_dialog_save))
            }
        },
        dismissButton = {
            Row {
                if (profile.weightOriginalValue != null) {
                    TextButton(onClick = onClear) {
                        Text(text = stringResource(R.string.personalization_weight_dialog_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    )
}

private fun formatWeightForInput(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
