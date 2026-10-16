package com.mkx.hrttracker.ui.settings

import androidx.compose.animation.core.LinearEasing
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.TextFieldColors
import androidx.compose.foundation.layout.Box
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.mkx.hrttracker.healthconnect.HEALTH_CONNECT_READ_WEIGHT_PERMISSION
import com.mkx.hrttracker.healthconnect.healthConnectWeightPermissions
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
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
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.shortLabelRes
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import java.time.ZoneId

/** The sync switch's actions; from [rememberHealthConnectWeightSync]. */
internal class HealthConnectWeightSync(
    val onSyncEnabledChange: (Boolean) -> Unit,
    val showPermissionDenied: Boolean,
    val onOpenHealthConnect: () -> Unit,
)

/**
 * Turning sync on asks for READ weight first; a grant enables it, a denial
 * leaves it off. Shared by Settings and onboarding.
 */
@Composable
internal fun rememberHealthConnectWeightSync(
    hasAccess: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
): HealthConnectWeightSync {
    val context = LocalContext.current
    // Set by a denial: the sheet then points to Health Connect's settings, since
    // after repeated denials its dialog stops showing and the app can't tell.
    var denied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        denied = HEALTH_CONNECT_READ_WEIGHT_PERMISSION !in granted
        if (!denied) onEnable()
    }
    // Access granted in Health Connect after a denial finishes the enable.
    LaunchedEffect(hasAccess) {
        if (hasAccess && denied) {
            denied = false
            onEnable()
        }
    }
    return HealthConnectWeightSync(
        onSyncEnabledChange = { enabled ->
            when {
                !enabled -> onDisable()
                hasAccess -> onEnable()
                else -> permissionLauncher.launch(healthConnectWeightPermissions(context))
            }
        },
        showPermissionDenied = denied && !hasAccess,
        onOpenHealthConnect = {
            try {
                context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // Health Connect went away; the card shows "not installed".
            }
        },
    )
}

/** The body-weight sheet with the Health Connect card. */
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
    showPermissionDenied: Boolean = false,
    onOpenHealthConnect: () -> Unit = {},
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
            showPermissionDenied = showPermissionDenied,
            onOpenHealthConnect = onOpenHealthConnect,
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
            title = { Text(text = stringResource(R.string.personalization_weight_clear_title)) },
            text = { Text(text = stringResource(R.string.personalization_weight_clear_confirmation)) },
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
    showPermissionDenied: Boolean = false,
    onOpenHealthConnect: () -> Unit = {},
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
    // which covers the placeholder -> real profile load.
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
        // AdjustStockSheet's rhythm: 8dp between blocks, the title's own 4/10dp around it.
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
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
                .padding(top = 4.dp, bottom = 10.dp)
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
                    enabled = !syncOn,
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
                val measuredAt = profile.weightMeasuredAt
                Text(
                    text = when {
                        showValidationError ->
                            stringResource(R.string.personalization_weight_validation_invalid)
                        // The synced weight's date, shown with the field's value.
                        syncOn && measuredAt != null -> {
                            val zone = ZoneId.systemDefault()
                            stringResource(
                                R.string.personalization_weight_dialog_description_recorded,
                                dateLabelFormatter(rememberAppLocale(), LocalDate.now(zone))(
                                    measuredAt.atZone(zone).toLocalDate()
                                ),
                            )
                        }
                        else -> stringResource(R.string.personalization_weight_dialog_description)
                    }
                )
            },
            colors = readOnlyFadeColors(readOnly = syncOn),
        )

        HealthConnectSection(
            profile = profile,
            syncEnabled = syncEnabled,
            syncOn = syncOn,
            healthConnect = healthConnect,
            isSyncing = isSyncing,
            hasNoReading = hasNoHealthConnectWeight,
            showPermissionDenied = showPermissionDenied,
            onToggle = { onSyncEnabledChange(!syncOn) },
            onSyncNow = onSyncNow,
            onOpenHealthConnect = onOpenHealthConnect,
        )

        // Same height in every state. Clear/Cancel shares Save's width and
        // animates its width away while syncing, so Save grows to fill the row.
        val leftWeight by animateFloatAsState(
            targetValue = if (syncOn) 0f else 1f,
            label = "weightSheetLeftButton",
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            if (leftWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(leftWeight)
                        .clipToBounds()
                        .graphicsLayer { alpha = leftWeight },
                ) {
                    HrtFilledTonalButton(
                        text = stringResource(
                            if (hasWeight) R.string.personalization_weight_dialog_clear else R.string.cancel
                        ),
                        onClick = {
                            if (!isInProgress) {
                                if (hasWeight) onClearRequest() else onClose()
                            }
                        },
                        // Shrinks with its slot down to its natural width, then
                        // clips, so the label never wraps mid-animation.
                        modifier = Modifier
                            .atLeastNaturalWidth()
                            .padding(end = 8.dp),
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

/**
 * Material 3 animates a disabled field's label but snaps its outline, so they
 * finish at different times. One shared fraction drives every color instead,
 * and the enabled and disabled slots get the same value, so the whole field
 * fades together into the standard disabled colors.
 */
@Composable
private fun readOnlyFadeColors(readOnly: Boolean): TextFieldColors {
    val fraction by animateFloatAsState(
        targetValue = if (readOnly) 1f else 0f,
        label = "weightFieldReadOnly",
    )
    val scheme = MaterialTheme.colorScheme
    val faded = scheme.onSurface.copy(alpha = 0.38f)
    val text = lerp(scheme.onSurface, faded, fraction)
    val muted = lerp(scheme.onSurfaceVariant, faded, fraction)
    val border = lerp(scheme.outline, scheme.onSurface.copy(alpha = 0.12f), fraction)
    return OutlinedTextFieldDefaults.colors(
        unfocusedTextColor = text,
        disabledTextColor = text,
        unfocusedBorderColor = border,
        disabledBorderColor = border,
        unfocusedLabelColor = muted,
        disabledLabelColor = muted,
        unfocusedTrailingIconColor = muted,
        disabledTrailingIconColor = muted,
        unfocusedSupportingTextColor = muted,
        disabledSupportingTextColor = muted,
    )
}

/** Fills the incoming width, but never measures narrower than its content wants. */
private fun Modifier.atLeastNaturalWidth(): Modifier = layout { measurable, constraints ->
    val width = maxOf(constraints.maxWidth, measurable.maxIntrinsicWidth(constraints.maxHeight))
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
}

/** The field's trailing content: the unit, then a button that converts kg <-> lb. */
@Composable
private fun WeightUnitSwitch(
    unit: WeightUnit,
    enabled: Boolean,
    onSwitch: (WeightUnit) -> Unit,
) {
    val unitLabel = stringResource(unit.shortLabelRes)
    val otherUnit = unit.other()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = unitLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
        IconButton(
            onClick = { onSwitch(otherUnit) },
            enabled = enabled,
            // The field already fades its trailing color while syncing; the
            // default disabled alpha would fade the icon a second time.
            colors = IconButtonDefaults.iconButtonColors(
                disabledContentColor = LocalContentColor.current,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sync_alt),
                contentDescription = stringResource(
                    R.string.personalization_weight_unit_a11y,
                    unitLabel,
                    stringResource(otherUnit.shortLabelRes),
                ),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The sync row and, after a denial, a message to open Health Connect, grouped
 * like Settings' reminders switch and its notification message. surfaceContainer
 * sits one step above the sheet's surfaceContainerLow.
 */
@Composable
private fun HealthConnectSection(
    profile: UserProfile,
    syncEnabled: Boolean,
    syncOn: Boolean,
    healthConnect: HealthConnectCapabilityState,
    isSyncing: Boolean,
    hasNoReading: Boolean,
    showPermissionDenied: Boolean,
    onToggle: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenHealthConnect: () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val status = healthConnectStatusText(profile, syncEnabled, healthConnect, isSyncing, hasNoReading)
    HrtSection(title = null) {
        item {
            PreferenceSegmentedListItem(
                title = stringResource(R.string.health_connect_title),
                supportingText = status,
                onClick = onToggle,
                enabled = healthConnect.sdkAvailable,
                containerColor = containerColor,
                supportingTextStyle = MaterialTheme.typography.bodySmall,
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_health_connect),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (syncOn) {
                            SyncNowButton(isSyncing = isSyncing, onClick = onSyncNow)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val switchDescription = stringResource(R.string.health_connect_switch_a11y)
                        Switch(
                            checked = syncOn,
                            onCheckedChange = { onToggle() },
                            enabled = healthConnect.sdkAvailable,
                            modifier = Modifier.semantics { contentDescription = switchDescription },
                        )
                    }
                },
            )
        }
        // Health Connect stops showing its dialog after repeated denials, with
        // no signal to the app, so any denial points to its settings instead.
        animatedItem(visible = showPermissionDenied) {
            SupportMessageListItem(
                text = stringResource(R.string.health_connect_permission_denied),
                painter = painterResource(R.drawable.ic_error_outline),
                leadingIconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onOpenHealthConnect,
                showChevron = true,
                containerColor = containerColor,
            )
        }
    }
}

/** Compact, so the status line keeps its width; spins while syncing. */
@Composable
private fun SyncNowButton(isSyncing: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
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

@Composable
private fun healthConnectStatusText(
    profile: UserProfile,
    syncEnabled: Boolean,
    healthConnect: HealthConnectCapabilityState,
    isSyncing: Boolean,
    hasNoReading: Boolean,
): String {
    return when {
        !healthConnect.sdkAvailable -> stringResource(R.string.health_connect_status_not_installed)
        syncEnabled && !healthConnect.hasAccess ->
            stringResource(R.string.health_connect_status_permission_off)
        !syncEnabled -> stringResource(R.string.health_connect_status_off)
        // No measured time without a sync that found nothing means a sync is on
        // its way (sync just turned on, or this process's first one).
        // ponytail: a sync that throws leaves this until the next one runs.
        isSyncing || (!hasNoReading && profile.weightMeasuredAt == null) ->
            stringResource(R.string.health_connect_status_syncing)
        hasNoReading -> stringResource(R.string.health_connect_status_no_reading)
        // The reading's date sits under the weight field.
        else -> stringResource(R.string.health_connect_status_synced)
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

internal fun formatWeightForInput(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
