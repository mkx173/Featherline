package com.mkx.hrttracker.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.roundWeightToTenth
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityState
import com.mkx.hrttracker.healthconnect.WeightSyncCoordinator
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HazeModalBottomSheet
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.LocalChromeHazeState
import com.mkx.hrttracker.ui.components.LocalHazeBlurEnabled
import com.mkx.hrttracker.ui.components.formatWeightForInput
import com.mkx.hrttracker.ui.components.hazeSheetSurface
import com.mkx.hrttracker.ui.components.shortLabelRes
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.LocalDate
import java.time.ZoneId

/**
 * Settings' body-weight sheet with the Health Connect card. Onboarding keeps
 * WeightDialog: sync can't be on that early.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeightSheet(
    profile: UserProfile,
    syncEnabled: Boolean,
    healthConnect: HealthConnectCapabilityState,
    isSyncing: Boolean,
    hasNoHealthConnectWeight: Boolean,
    isInProgress: Boolean,
    onSave: (Double, WeightUnit) -> Unit,
    onClear: () -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val scope = rememberCoroutineScope()
    // Shown beside the sheet, not inside its window: a dialog composed in the
    // sheet's content stretches edge to edge.
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    val close = { hideBottomSheet(scope, sheetState, onDismissRequest) }
    val navigationBarBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    HazeModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (!isInProgress) onDismissRequest() },
        modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
    ) {
        WeightSheetContent(
            profile = profile,
            syncEnabled = syncEnabled,
            healthConnect = healthConnect,
            isSyncing = isSyncing,
            hasNoHealthConnectWeight = hasNoHealthConnectWeight,
            isInProgress = isInProgress,
            onSave = { value, unit ->
                onSave(value, unit)
                close()
            },
            onClearRequest = { showClearConfirm = true },
            onSyncEnabledChange = onSyncEnabledChange,
            onSyncNow = onSyncNow,
            onClose = close,
            modifier = Modifier.padding(
                start = dimensionResource(R.dimen.padding_large),
                end = dimensionResource(R.dimen.padding_large),
                bottom = dimensionResource(R.dimen.padding_large) + navigationBarBottomPadding,
            ),
        )
    }

    if (showClearConfirm) {
        HazeAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(text = stringResource(R.string.personalization_weight_clear_confirm_title)) },
            text = { Text(text = stringResource(R.string.personalization_weight_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                        close()
                    }
                ) {
                    Text(text = stringResource(R.string.personalization_weight_dialog_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun WeightSheetContent(
    profile: UserProfile,
    syncEnabled: Boolean,
    healthConnect: HealthConnectCapabilityState,
    isSyncing: Boolean,
    hasNoHealthConnectWeight: Boolean,
    isInProgress: Boolean,
    onSave: (Double, WeightUnit) -> Unit,
    onClearRequest: () -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Follows the switch's checked state. With sync on, Health Connect is the
    // source and the weight is read-only; turn sync off to type one.
    val syncOn = syncEnabled && healthConnect.hasAccess
    val hasWeight = profile.weightOriginalValue != null
    var valueText by rememberSaveable {
        mutableStateOf(profile.weightOriginalValue?.let(::formatWeightForInput).orEmpty())
    }
    var selectedUnit by rememberSaveable { mutableStateOf(profile.weightOriginalUnit) }
    var isDirty by rememberSaveable { mutableStateOf(false) }
    var showValidationError by rememberSaveable { mutableStateOf(false) }

    // With sync on the field always shows the profile, so a sync landing while
    // the sheet is open updates it live, and turning sync on drops typed text.
    // With sync off it re-seeds only until the user types or switches the unit,
    // which covers the placeholder -> real profile load (see WeightDialog).
    LaunchedEffect(profile.weightOriginalValue, profile.weightOriginalUnit, syncOn) {
        if (syncOn || !isDirty) {
            valueText = profile.weightOriginalValue?.let(::formatWeightForInput).orEmpty()
            selectedUnit = profile.weightOriginalUnit
            isDirty = false
            showValidationError = false
        }
    }

    val submit = {
        if (!isInProgress) {
            val parsed = parseWeightText(valueText)
            if (parsed == null) {
                showValidationError = true
            } else {
                onSave(parsed, selectedUnit)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
    ) {
        // On API 26 the sheet window auto-focuses the first text field and opens
        // the IME on entry; parking focus on the (non-text) title avoids it, like
        // AdjustStockSheet's anchor. On the title so it takes no spacedBy slot.
        val dismissFocusAnchor = remember { FocusRequester() }
        LaunchedEffect(Unit) { dismissFocusAnchor.requestFocus() }
        Text(
            text = stringResource(R.string.personalization_weight_dialog_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(top = 4.dp)
                .focusRequester(dismissFocusAnchor)
                .focusable(),
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = valueText,
            onValueChange = {
                if (isInProgress) return@OutlinedTextField
                valueText = it
                isDirty = true
                showValidationError = false
            },
            enabled = !syncOn,
            label = {
                Text(text = stringResource(R.string.personalization_weight_dialog_value_label))
            },
            trailingIcon = {
                WeightUnitSwitch(
                    unit = selectedUnit,
                    showSwitch = !syncOn,
                    onSwitch = { otherUnit ->
                        if (!isInProgress) {
                            valueText = convertWeightText(valueText, selectedUnit, otherUnit)
                            selectedUnit = otherUnit
                            isDirty = true
                        }
                    },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            isError = showValidationError,
            supportingText = {
                Text(
                    text = stringResource(
                        if (showValidationError) {
                            R.string.personalization_weight_validation_invalid
                        } else {
                            R.string.personalization_weight_dialog_description
                        }
                    )
                )
            },
            // The default disabled colors (38 %) make a read-only value hard to read.
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        HealthConnectCard(
            profile = profile,
            syncEnabled = syncEnabled,
            syncOn = syncOn,
            healthConnect = healthConnect,
            isSyncing = isSyncing,
            hasNoReading = hasNoHealthConnectWeight,
            onToggle = { onSyncEnabledChange(!syncOn) },
            onSyncNow = onSyncNow,
        )

        // Same height in every state: buttons share one min height.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!syncOn) {
                if (hasWeight) {
                    HrtOutlinedButton(
                        text = stringResource(R.string.personalization_weight_dialog_clear),
                        onClick = { if (!isInProgress) onClearRequest() },
                    )
                } else {
                    HrtOutlinedButton(
                        text = stringResource(R.string.cancel),
                        onClick = { if (!isInProgress) onClose() },
                    )
                }
            }
            HrtButton(
                text = stringResource(
                    if (syncOn) R.string.confirm else R.string.personalization_weight_dialog_save
                ),
                onClick = { if (syncOn) onClose() else submit() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The field's trailing content: the unit, then a button that converts kg <-> lb. */
@Composable
private fun WeightUnitSwitch(
    unit: WeightUnit,
    showSwitch: Boolean,
    onSwitch: (WeightUnit) -> Unit,
) {
    val unitLabel = stringResource(unit.shortLabelRes)
    val otherUnit = unit.other()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = unitLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp, end = if (showSwitch) 0.dp else 16.dp),
        )
        if (showSwitch) {
            IconButton(onClick = { onSwitch(otherUnit) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_sync_alt),
                    contentDescription = stringResource(
                        R.string.personalization_weight_unit_a11y,
                        unitLabel,
                        stringResource(otherUnit.shortLabelRes),
                    ),
                )
            }
        }
    }
}

@Composable
private fun HealthConnectCard(
    profile: UserProfile,
    syncEnabled: Boolean,
    syncOn: Boolean,
    healthConnect: HealthConnectCapabilityState,
    isSyncing: Boolean,
    hasNoReading: Boolean,
    onToggle: () -> Unit,
    onSyncNow: () -> Unit,
) {
    // Same container as HomeCardLayoutRow: secondaryContainer when on,
    // surfaceContainerHighest when off, a haze tint on haze sheets.
    val containerColor by animateColorAsState(
        targetValue = if (syncOn) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "healthConnectCardContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (syncOn) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "healthConnectCardContent",
    )
    val hazeBlur = LocalHazeBlurEnabled.current && LocalChromeHazeState.current != null
    val shape = MaterialTheme.shapes.large
    Surface(
        onClick = onToggle,
        enabled = healthConnect.sdkAvailable,
        modifier = Modifier.hazeSheetSurface(
            containerColor = containerColor,
            shape = shape,
            active = hazeBlur,
        ),
        shape = shape,
        color = if (hazeBlur) Color.Transparent else containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                Text(
                    text = stringResource(R.string.health_connect_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = healthConnectStatusText(
                        profile,
                        syncEnabled,
                        healthConnect,
                        isSyncing,
                        hasNoReading,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (syncOn) {
                // Compact like HomeCardLayoutRow's trailing button, so the status
                // line keeps its width; the card itself stays the large target.
                IconButton(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier.size(36.dp),
                ) {
                    val rotation = if (isSyncing) {
                        rememberInfiniteTransition(label = "healthConnectSync").animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    // One turn per minimum syncing time, so a fast sync shows a whole turn.
                                    durationMillis = WeightSyncCoordinator.MIN_SYNCING_MILLIS.toInt(),
                                    easing = LinearEasing,
                                ),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "healthConnectSyncRotation",
                        ).value
                    } else {
                        0f
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_sync),
                        contentDescription = stringResource(R.string.health_connect_sync_now),
                        // The icon spins counter-clockwise, like its arrows.
                        modifier = Modifier.rotate(-rotation),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val switchDescription = stringResource(R.string.health_connect_switch_a11y)
            Switch(
                checked = syncOn,
                onCheckedChange = { onToggle() },
                enabled = healthConnect.sdkAvailable,
                modifier = Modifier.semantics { contentDescription = switchDescription },
            )
        }
    }
}

@Composable
private fun healthConnectStatusText(
    profile: UserProfile,
    syncEnabled: Boolean,
    healthConnect: HealthConnectCapabilityState,
    isSyncing: Boolean,
    hasNoReading: Boolean,
): String {
    val measuredAt = profile.weightMeasuredAt
    return when {
        !healthConnect.sdkAvailable -> stringResource(R.string.health_connect_status_not_installed)
        syncEnabled && !healthConnect.hasAccess ->
            stringResource(R.string.health_connect_status_permission_off)
        !syncEnabled -> stringResource(R.string.health_connect_status_off)
        isSyncing -> stringResource(R.string.health_connect_status_syncing)
        // No measured time also means the weight didn't come from HC (e.g. the
        // first sync of this process hasn't finished).
        hasNoReading || measuredAt == null ->
            stringResource(R.string.health_connect_status_no_reading)
        else -> {
            val locale = rememberAppLocale()
            val zone = ZoneId.systemDefault()
            val dateTime = measuredAt.atZone(zone)
            stringResource(
                R.string.health_connect_status_measured,
                dateLabelFormatter(locale, LocalDate.now(zone))(dateTime.toLocalDate()),
                rememberLocalizedShortTimeFormatter(locale).format(dateTime),
            )
        }
    }
}

private fun WeightUnit.other(): WeightUnit = when (this) {
    WeightUnit.KILOGRAMS -> WeightUnit.POUNDS
    WeightUnit.POUNDS -> WeightUnit.KILOGRAMS
}

internal fun parseWeightText(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

/** Converts typed text between units, rounded to 0.1; empty or invalid text is left alone. */
internal fun convertWeightText(text: String, from: WeightUnit, to: WeightUnit): String {
    val parsed = parseWeightText(text) ?: return text
    return formatWeightForInput(roundWeightToTenth(parsed * from.kgPerUnit / to.kgPerUnit))
}

