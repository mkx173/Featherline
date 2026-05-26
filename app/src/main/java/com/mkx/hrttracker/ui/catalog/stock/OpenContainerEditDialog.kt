package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import java.math.BigDecimal

@Composable
fun OpenContainerEditDialog(
    preparation: MedicinePreparation,
    currentAmount: Double?,
    onSubmit: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val containerSize = preparation.containerSizeOrZero()
    val unitRes = preparation.unitShortRes() ?: run {
        // Pool preparations have no open container; nothing to render.
        onDismiss()
        return
    }
    val titleRes = preparation.titleRes()

    var valueText by remember(currentAmount) {
        mutableStateOf(currentAmount.toEditableText())
    }
    var showError by remember { mutableStateOf(false) }

    val submit = {
        val parsed = valueText.trim().toDoubleOrNull()
        if (parsed == null || parsed < 0.0) {
            showError = true
        } else {
            onSubmit(parsed.coerceAtMost(containerSize))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(titleRes)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.padding_small),
                ),
            ) {
                Text(
                    text = stringResource(
                        R.string.stock_open_edit_dialog_body,
                        formatContainerSize(containerSize),
                        stringResource(unitRes),
                    ),
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = {
                        valueText = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.stock_open_edit_dialog_field)) },
                    suffix = { Text(stringResource(unitRes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.stock_open_edit_dialog_invalid)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text(stringResource(R.string.stock_adjust_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.stock_cancel))
            }
        },
    )
}

private fun MedicinePreparation.containerSizeOrZero(): Double = when (this) {
    is MedicinePreparation.InjectionMultiUseVial -> vialVolumeMl
    is MedicinePreparation.GelContainer -> containerWeightGrams
    else -> 0.0
}

private fun MedicinePreparation.unitShortRes(): Int? = when (this) {
    is MedicinePreparation.InjectionMultiUseVial -> R.string.unit_ml
    is MedicinePreparation.GelContainer -> R.string.unit_grams
    else -> null
}

private fun MedicinePreparation.titleRes(): Int = when (this) {
    is MedicinePreparation.GelContainer -> R.string.stock_open_edit_dialog_title_pump
    else -> R.string.stock_open_edit_dialog_title_vial
}

private fun Double?.toEditableText(): String {
    val value = this ?: return ""
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

private fun formatContainerSize(value: Double): String {
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
