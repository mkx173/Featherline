package com.mkx.hrttracker.ui.catalog.stock

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.data.repository.StockRecount
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.ui.catalog.AdjustSheetTab
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.medication.activeDoseAssistPresets
import java.math.BigDecimal
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustStockSheet(
    projection: MedicineStockProjection,
    initialTab: AdjustSheetTab,
    receivedOnly: Boolean = false,
    previewRunway: (MedicineStock) -> RunwayProjection?,
    onRecount: (StockRecount) -> Unit,
    onReceived: (StockReceived) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val effectiveInitialTab = if (receivedOnly) AdjustSheetTab.RECEIVED else initialTab
    var activeTab by remember(effectiveInitialTab) { mutableStateOf(effectiveInitialTab) }
    val isContainer = projection.medicine.preparation is MedicinePreparation.InjectionMultiUseVial ||
        projection.medicine.preparation is MedicinePreparation.GelContainer

    val density = LocalDensity.current
    val navigationBarBottomPadding = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
        contentWindowInsets = { WindowInsets.systemBars.only(WindowInsetsSides.Top) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimensionResource(R.dimen.padding_large),
                    end = dimensionResource(R.dimen.padding_large),
                    bottom = dimensionResource(R.dimen.padding_large) + navigationBarBottomPadding,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stock_adjust_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 10.dp, top = 4.dp),
                )
                HrtFilledTonalButton(
                    text = stringResource(R.string.stock_cancel),
                    onClick = onDismissRequest,
                )
            }

            Spacer(Modifier.height(dimensionResource(R.dimen.padding_small)))

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified
            ) {
                ConnectedButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    options = AdjustSheetTab.entries,
                    selectedOption = activeTab,
                    optionLabel = { tab -> stringResource(tab.labelRes) },
                    onOptionSelected = { tab -> activeTab = tab },
                    enabled = !receivedOnly,
                    layout = ConnectedButtonGroupLayout.ROW,
                    expandOptions = true,
                )
            }

            Spacer(Modifier.height(dimensionResource(R.dimen.padding_small)))

            when (activeTab) {
                AdjustSheetTab.RECOUNT -> RecountForm(
                    projection = projection,
                    isContainer = isContainer,
                    previewRunway = previewRunway,
                    onSubmit = onRecount,
                    onDismiss = onDismissRequest,
                )

                AdjustSheetTab.RECEIVED -> ReceivedForm(
                    projection = projection,
                    isContainer = isContainer,
                    previewRunway = previewRunway,
                    onSubmit = onReceived,
                    onDismiss = onDismissRequest,
                )
            }
        }
    }
}

@Composable
private fun RecountForm(
    projection: MedicineStockProjection,
    isContainer: Boolean,
    previewRunway: (MedicineStock) -> RunwayProjection?,
    onSubmit: (StockRecount) -> Unit,
    onDismiss: () -> Unit,
) {
    val stock = projection.medicine.stock
    val allowDecimal = projection.medicine.preparation.allowsDecimalAdjustStockCount()
    val placeholderText = remember(stock.unitsRemaining, allowDecimal) {
        stock.unitsRemaining.toEditableCountOrEmpty(allowDecimal).ifEmpty { "0" }
    }
    var unitsRemainingText by remember(projection.medicine.uuid) { mutableStateOf("") }

    val unitsRemaining = parseAdjustStockCount(unitsRemainingText, allowDecimal)
    val canConfirm = unitsRemaining != null && unitsRemaining >= 0
    val effectiveUnitsRemaining = unitsRemaining ?: (stock.unitsRemaining ?: 0.0)
    val previewStock = stock.adjustPreviewStock(
        unitsRemaining = effectiveUnitsRemaining,
        isContainer = isContainer,
    )

    val stepRecount: (Int) -> Unit = { delta ->
        unitsRemainingText = stepAdjustStockCountText(
            unitsRemainingText.ifEmpty { placeholderText },
            delta,
            allowDecimal,
        )
    }

    Column {
        StockStepperCard(
            label = stringResource(R.string.stock_adjust_field_current_stock),
            value = unitsRemainingText,
            unit = adjustStockUnitLabel(projection.medicine.preparation),
            leadingIconRes = R.drawable.ic_box_edit,
            placeholder = placeholderText,
            allowDecimal = allowDecimal,
            onValueChange = { unitsRemainingText = it },
            onStep = stepRecount,
        )
        val presets = quickAddPresets(projection.medicine.preparation)
        if (presets.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            QuickAddChips(
                presets = presets,
                onAdd = stepRecount,
            )
        }
        AfterPreview(
            projection = projection,
            hypotheticalStock = previewStock,
            previewRunway = previewRunway,
        )
        HrtButton(
            text = stringResource(R.string.stock_adjust_save),
            enabled = canConfirm,
            onClick = {
                val resolvedUnitsRemaining = unitsRemaining ?: return@HrtButton
                onSubmit(StockRecount(unitsRemaining = resolvedUnitsRemaining))
                onDismiss()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.padding_small)),
        )
    }
}

@Composable
private fun ReceivedForm(
    projection: MedicineStockProjection,
    isContainer: Boolean,
    previewRunway: (MedicineStock) -> RunwayProjection?,
    onSubmit: (StockReceived) -> Unit,
    onDismiss: () -> Unit,
) {
    val allowDecimal = projection.medicine.preparation.allowsDecimalAdjustStockCount()
    val initialReceivedText = remember(
        projection.medicine.uuid,
        projection.medicine.stock.trackingEnabled,
        projection.medicine.stock.unitsRemaining,
        projection.medicine.stock.openContainerAmount,
    ) {
        if (
            !projection.medicine.stock.trackingEnabled &&
            storedTotalUnits(projection) > 0.0
        ) {
            "0"
        } else {
            ""
        }
    }
    var receivedText by remember(initialReceivedText) { mutableStateOf(initialReceivedText) }

    val received = parseAdjustStockCount(receivedText, allowDecimal)
    val canConfirm = received != null && received >= 0
    val effectiveUnitsRemaining = if (isContainer) {
        (projection.medicine.stock.unitsRemaining ?: 0.0) + (received ?: 0.0)
    } else {
        storedTotalUnits(projection) + (received ?: 0.0)
    }
    val previewStock = projection.medicine.stock.adjustPreviewStock(
        unitsRemaining = effectiveUnitsRemaining,
        isContainer = isContainer,
    )

    val stepReceived: (Int) -> Unit = { delta ->
        receivedText = stepAdjustStockCountText(receivedText, delta, allowDecimal)
    }

    Column {
        StockStepperCard(
            label = stringResource(R.string.stock_adjust_field_add_to_stock),
            value = receivedText,
            unit = adjustStockUnitLabel(projection.medicine.preparation),
            leadingIconRes = R.drawable.ic_box_add,
            allowDecimal = allowDecimal,
            onValueChange = { receivedText = it },
            onStep = stepReceived,
        )
        val presets = quickAddPresets(projection.medicine.preparation)
        if (presets.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            QuickAddChips(
                presets = presets,
                onAdd = stepReceived,
            )
        }
        AfterPreview(
            projection = projection,
            hypotheticalStock = previewStock,
            previewRunway = previewRunway,
        )
        HrtButton(
            text = stringResource(R.string.stock_adjust_add),
            enabled = canConfirm,
            onClick = {
                val resolvedReceived = received ?: return@HrtButton
                onSubmit(StockReceived(unitsReceived = resolvedReceived))
                onDismiss()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.padding_small)),
        )
    }
}

@Composable
private fun AfterPreview(
    projection: MedicineStockProjection,
    hypotheticalStock: MedicineStock,
    previewRunway: (MedicineStock) -> RunwayProjection?,
) {
    val preparation = projection.medicine.preparation
    val runway = previewRunway(hypotheticalStock)?.let { runwayProjection ->
        adjustPreviewRunwayText(runwayProjection)
    }?.resolve()
    val displayCount = hypotheticalStock.unitsRemaining ?: 0.0
    val title = stringResource(
        R.string.stock_adjust_after,
        formatCount(displayCount),
        adjustStockUnitLabel(preparation),
    )
    SupportMessageListItem(
        text = title,
        supportingText = runway,
        painter = painterResource(R.drawable.ic_arrow_forward),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
}

private fun MedicineStock.adjustPreviewStock(
    unitsRemaining: Double,
    isContainer: Boolean,
): MedicineStock {
    val resolvedUnitsRemaining = unitsRemaining.coerceAtLeast(0.0)
    return copy(
        trackingEnabled = true,
        unitsRemaining = resolvedUnitsRemaining,
        unitsLastTotal = resolvedUnitsRemaining,
        openContainerAmount = if (isContainer && trackingEnabled) openContainerAmount else null,
    )
}

internal data class AdjustPreviewRunwayText(
    @param:StringRes val resId: Int,
    val intArg: Int? = null,
)

internal fun adjustPreviewRunwayText(runway: RunwayProjection): AdjustPreviewRunwayText? {
    return when (runway) {
        is RunwayProjection.Days -> AdjustPreviewRunwayText(
            resId = R.string.stock_runway_days_remaining,
            intArg = runway.days,
        )
        RunwayProjection.BeyondHorizon -> AdjustPreviewRunwayText(
            resId = R.string.stock_runway_more_than_one_year,
        )
        RunwayProjection.NoSchedule -> null
    }
}

@Composable
private fun AdjustPreviewRunwayText.resolve(): String {
    return if (intArg == null) {
        stringResource(resId)
    } else {
        stringResource(resId, intArg)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StockStepperCard(
    label: String,
    value: String,
    unit: String,
    @DrawableRes leadingIconRes: Int,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    placeholder: String = "0",
    allowDecimal: Boolean,
) {
    val effectiveCount = parseAdjustStockCount(value.ifEmpty { placeholder }, allowDecimal) ?: 0.0
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    EditorSegmentedListItem(
        index = 0,
        count = 1,
        onClick = { focusRequester.requestFocus() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        overlineContent = {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { onStep(-1) },
                    enabled = effectiveCount > 0,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.stock_adjust_decrease_count),
                    )
                }
                FilledTonalIconButton(
                    onClick = { onStep(1) },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.stock_adjust_increase_count),
                    )
                }
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            val numberStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(modifier = Modifier.alignByBaseline()) {
                // Invisible sizer (or hint placeholder when empty) — keeps the
                // editable field's width tied to the typed text so the unit
                // label can sit immediately next to the number. The trailing
                // padding reserves room for the text cursor so it isn't clipped
                // (and doesn't trigger the field's internal horizontal scroll)
                // when the caret sits at the end of the value.
                Text(
                    text = value.ifEmpty { placeholder },
                    style = numberStyle,
                    color = if (value.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier.padding(end = 2.dp),
                )
                BasicTextField(
                    value = value,
                    onValueChange = {
                        onValueChange(sanitizeAdjustStockCountText(it, allowDecimal))
                    },
                    textStyle = numberStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (allowDecimal) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        },
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .matchParentSize()
                        .focusRequester(focusRequester),
                )
            }
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun QuickAddChips(
    presets: List<Int>,
    onAdd: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            AssistChip(
                onClick = { onAdd(preset) },
                label = { Text(text = "+$preset") },
            )
        }
    }
}

private fun quickAddPresets(preparation: MedicinePreparation): List<Int> = when (preparation) {
    is MedicinePreparation.Pill,
    is MedicinePreparation.Capsule -> listOf(10, 14, 21, 28)
    is MedicinePreparation.InjectionSingleUseVial -> listOf(5, 10)
    is MedicinePreparation.Patch -> listOf(8, 12, 24)
    else -> emptyList()
}

@Composable
private fun adjustStockUnitLabel(preparation: MedicinePreparation): String {
    val unitRes = when (preparation) {
        is MedicinePreparation.InjectionMultiUseVial -> R.string.stock_unit_vials
        is MedicinePreparation.GelContainer -> R.string.stock_unit_containers
        else -> stockUnitRes(preparation)
    }
    return if (unitRes != null) stringResource(unitRes) else ""
}

internal fun parseAdjustStockCount(
    text: String,
    allowDecimal: Boolean,
): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.any { !it.isDigit() && it != '.' && it != ',' }) return null
    if (!allowDecimal && trimmed.any { it == '.' || it == ',' }) return null
    val normalized = if (allowDecimal) {
        val separatorCount = trimmed.count { it == '.' || it == ',' }
        if (separatorCount > 1) return null
        trimmed.replace(',', '.')
    } else {
        trimmed
    }
    val value = normalized.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < 0.0) return null
    return value
}

internal fun sanitizeAdjustStockCountText(
    input: String,
    allowDecimal: Boolean,
): String {
    var hasSeparator = false
    val filtered = StringBuilder()
    input.forEach { char ->
        when {
            char.isDigit() -> filtered.append(char)
            allowDecimal && (char == '.' || char == ',') && !hasSeparator -> {
                filtered.append(char)
                hasSeparator = true
            }
        }
    }
    return filtered.toString()
}

internal fun stepAdjustStockCountText(
    current: String,
    delta: Int,
    allowDecimal: Boolean,
): String {
    val next = ((parseAdjustStockCount(current, allowDecimal) ?: 0.0) + delta)
        .coerceAtLeast(0.0)
    return formatEditableStockCount(next, allowDecimal)
}

private fun MedicinePreparation.containerSizeUnits(): Double {
    return when (this) {
        is MedicinePreparation.InjectionMultiUseVial -> vialVolumeMl
        is MedicinePreparation.GelContainer -> containerWeightGrams
        else -> 0.0
    }
}

private fun storedTotalUnits(projection: MedicineStockProjection): Double {
    val stock = projection.medicine.stock
    val preparation = projection.medicine.preparation
    return when (preparation) {
        is MedicinePreparation.InjectionMultiUseVial,
        is MedicinePreparation.GelContainer -> {
            val containerSize = preparation.containerSizeUnits()
            (stock.unitsRemaining ?: 0.0) * containerSize +
                (stock.openContainerAmount ?: 0.0)
        }

        else -> stock.unitsRemaining ?: 0.0
    }
}

private fun formatCount(value: Double): String {
    val formatted = String.format(Locale.getDefault(), "%.2f", value)
    if (!formatted.contains('.')) return formatted
    val trimmed = formatted.trimEnd('0').trimEnd('.')
    return trimmed.ifEmpty { "0" }
}

private val AdjustSheetTab.labelRes: Int
    get() = when (this) {
        AdjustSheetTab.RECOUNT -> R.string.stock_adjust_tab_recount
        AdjustSheetTab.RECEIVED -> R.string.stock_adjust_tab_received
    }

private fun MedicinePreparation.allowsDecimalAdjustStockCount(): Boolean {
    return this is MedicinePreparation.Pill
}

private fun Double?.toEditableCountOrEmpty(allowDecimal: Boolean): String {
    val value = this ?: return ""
    return formatEditableStockCount(value, allowDecimal)
}

private fun formatEditableStockCount(value: Double, allowDecimal: Boolean): String {
    val resolved = value.coerceAtLeast(0.0)
    if (!allowDecimal) return resolved.toLong().toString()
    return BigDecimal.valueOf(resolved).stripTrailingZeros().toPlainString()
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 420)
@Composable
private fun AdjustStockSheetPillRecountPreview() {
    AdjustStockSheetPreviewContainer {
        RecountForm(
            projection = previewProjection(
                medicine = previewMedicine(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                    stock = com.mkx.hrttracker.model.medication.MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 18.0,
                    ),
                ),
                dosesPerDayMagnitude = 1.0,
                totalStockUnits = 18.0,
                runwayDays = 18.0,
                state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY,
            ),
            isContainer = false,
            previewRunway = { null },
            onSubmit = {},
            onDismiss = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 420)
@Composable
private fun AdjustStockSheetSingleUseVialReceivedPreview() {
    AdjustStockSheetPreviewContainer {
        ReceivedForm(
            projection = previewProjection(
                medicine = previewMedicine(
                    preparation = MedicinePreparation.InjectionSingleUseVial(
                        strengthMgPerVial = 5.0,
                    ),
                    stock = com.mkx.hrttracker.model.medication.MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 3.0,
                    ),
                ),
                dosesPerDayMagnitude = 0.14,
                totalStockUnits = 3.0,
                runwayDays = 21.0,
                state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY,
            ),
            isContainer = false,
            previewRunway = { null },
            onSubmit = {},
            onDismiss = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 420)
@Composable
private fun AdjustStockSheetMultiUseVialReceivedPreview() {
    val preparation = MedicinePreparation.InjectionMultiUseVial(
        concentrationMgPerMl = 10.0,
        vialVolumeMl = 5.0,
    )
    AdjustStockSheetPreviewContainer {
        ReceivedForm(
            projection = previewProjection(
                medicine = previewMedicine(
                    preparation = preparation,
                    stock = com.mkx.hrttracker.model.medication.MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 2.0,
                        openContainerAmount = 2.5,
                    ),
                ),
                dosesPerDayMagnitude = 0.2,
                totalStockUnits = 12.5,
                runwayDays = 62.5,
                state = com.mkx.hrttracker.model.medication.MedicineStockState.HEALTHY,
            ),
            isContainer = true,
            previewRunway = { null },
            onSubmit = {},
            onDismiss = {},
        )
    }
}

@Composable
private fun AdjustStockSheetPreviewContainer(content: @Composable () -> Unit) {
    com.mkx.hrttracker.ui.theme.HrtTrackerTheme(dynamicColor = false) {
        androidx.compose.material3.Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.padding_large)),
            ) {
                content()
            }
        }
    }
}
