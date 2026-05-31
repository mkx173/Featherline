package com.mkx.hrttracker.ui.medication

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R

/** Section header used across medicine-creation and dose-instruction sections. */
@Composable
internal fun MedicationEditorSectionLabel(text: String, topPadding: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp, top = if (topPadding) 4.dp else 0.dp),
    )
}

/** "Label (unit)" — keeps the unit visible in the field's resting label. */
@Composable
internal fun medicationEditorFieldLabelWithUnit(
    @StringRes labelRes: Int,
    @StringRes unitRes: Int,
): String = "${stringResource(labelRes)} (${stringResource(unitRes)})"

/**
 * Shared numeric text field for medicine-creation and dose-instruction inputs.
 *
 * Union of the two former private NumericField copies. The high-dose warning is
 * load-bearing for the create-then-dose flow (it surfaces on the strength field
 * since that flow has no summary card) and is rendered with the error tint —
 * the only tint that was ever live. `onImeNext` drives Next-key field jumps in
 * the create-medicine form; sheets that don't pass it get plain Done.
 */
@Composable
internal fun MedicationNumericField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    @StringRes errorMessageRes: Int? = null,
    @DrawableRes leadingIconRes: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    showWarningIcon: Boolean = false,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeNext: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        label = { Text(text = label) },
        placeholder = placeholder?.let { placeholderText -> { Text(text = placeholderText) } },
        suffix = suffix?.let { suffixText -> { Text(text = suffixText) } },
        leadingIcon = leadingIconRes?.let { iconRes ->
            { Icon(painter = painterResource(iconRes), contentDescription = null) }
        },
        trailingIcon = if (showWarningIcon) {
            {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = stringResource(R.string.medication_editor_dose_warning),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            null
        },
        supportingText = errorMessageRes?.let { messageRes ->
            { Text(text = stringResource(messageRes), color = MaterialTheme.colorScheme.error) }
        },
        modifier = modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onImeNext?.invoke() ?: focusManager.clearFocus() },
            onDone = { focusManager.clearFocus() },
        ),
    )
}
