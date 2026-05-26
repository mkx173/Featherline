package com.mkx.hrttracker.ui.catalog.stock

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.data.repository.StockRecount
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.ui.catalog.AdjustSheetTab
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import java.util.Locale
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustStockSheet(
    projection: MedicineStockProjection,
    initialTab: AdjustSheetTab,
    receivedOnly: Boolean = false,
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

            Spacer(Modifier.height(dimensionResource(R.dimen.padding_medium)))

            when (activeTab) {
                AdjustSheetTab.RECOUNT -> RecountForm(
                    projection = projection,
                    isContainer = isContainer,
                    onSubmit = onRecount,
                    onDismiss = onDismissRequest,
                )

                AdjustSheetTab.RECEIVED -> ReceivedForm(
                    projection = projection,
                    isContainer = isContainer,
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
    onSubmit: (StockRecount) -> Unit,
    onDismiss: () -> Unit,
) {
    val stock = projection.medicine.stock
    val placeholderText = remember(stock.unitsRemaining) {
        stock.unitsRemaining.toEditableCountOrEmpty().ifEmpty { "0" }
    }
    var unitsRemainingText by remember(projection.medicine.uuid) { mutableStateOf("") }

    val unitsRemaining = unitsRemainingText.toIntOrNull()
    val canConfirm = unitsRemaining != null && unitsRemaining >= 0
    val effectiveSealed = unitsRemaining?.toDouble() ?: (stock.unitsRemaining ?: 0.0)
    val simulatedTotal = if (isContainer) {
        val containerSize = projection.medicine.preparation.containerSizeUnits()
        effectiveSealed * containerSize
    } else {
        effectiveSealed
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StockStepperCard(
            label = stringResource(R.string.stock_adjust_field_current_stock),
            value = unitsRemainingText,
            unit = adjustStockUnitLabel(projection.medicine.preparation),
            leadingIconRes = R.drawable.ic_inventory,
            placeholder = placeholderText,
            onValueChange = { unitsRemainingText = it },
            onStep = { delta ->
                unitsRemainingText = stepCountText(
                    unitsRemainingText.ifEmpty { placeholderText },
                    delta,
                )
            },
        )
        AfterPreview(
            projection = projection,
            simulatedSealedUnits = simulatedTotal,
        )
        HrtButton(
            text = stringResource(R.string.stock_adjust_save),
            enabled = canConfirm,
            onClick = {
                val resolvedUnitsRemaining = unitsRemaining ?: return@HrtButton
                onSubmit(StockRecount(unitsRemaining = resolvedUnitsRemaining.toDouble()))
                onDismiss()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.padding_xsmall)),
        )
    }
}

@Composable
private fun ReceivedForm(
    projection: MedicineStockProjection,
    isContainer: Boolean,
    onSubmit: (StockReceived) -> Unit,
    onDismiss: () -> Unit,
) {
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

    val received = receivedText.toIntOrNull()
    val canConfirm = received != null && received >= 0
    val simulatedTotal = if (isContainer) {
        val containerSize = projection.medicine.preparation.containerSizeUnits()
        val sealed = (projection.medicine.stock.unitsRemaining ?: 0.0) + (received ?: 0).toDouble()
        sealed * containerSize
    } else {
        storedTotalUnits(projection) + (received ?: 0).toDouble()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StockStepperCard(
            label = stringResource(R.string.stock_adjust_field_add_to_stock),
            value = receivedText,
            unit = adjustStockUnitLabel(projection.medicine.preparation),
            leadingIconRes = R.drawable.ic_box_add,
            onValueChange = { receivedText = it },
            onStep = { delta -> receivedText = stepCountText(receivedText, delta) },
        )
        AfterPreview(
            projection = projection,
            simulatedSealedUnits = simulatedTotal,
        )
        HrtButton(
            text = stringResource(R.string.stock_adjust_add),
            enabled = canConfirm,
            onClick = {
                val resolvedReceived = received ?: return@HrtButton
                onSubmit(StockReceived(unitsReceived = resolvedReceived.toDouble()))
                onDismiss()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.padding_xsmall)),
        )
    }
}

@Composable
private fun AfterPreview(
    projection: MedicineStockProjection,
    simulatedSealedUnits: Double,
) {
    val preparation = projection.medicine.preparation
    val runwayTotalUnits = when (preparation) {
        is MedicinePreparation.InjectionMultiUseVial,
        is MedicinePreparation.GelContainer -> {
            val size = preparation.containerSizeUnits()
            val open = (projection.medicine.stock.openContainerAmount ?: 0.0)
                .coerceIn(0.0, size)
            simulatedSealedUnits + open
        }

        else -> simulatedSealedUnits
    }
    val rate = projection.dosesPerDayMagnitude
    val runway = if (rate > 0.0) {
        stringResource(
            R.string.stock_runway_days_remaining,
            floor(runwayTotalUnits / rate).toInt(),
        )
    } else {
        null
    }
    val displayCount = when (preparation) {
        is MedicinePreparation.InjectionMultiUseVial,
        is MedicinePreparation.GelContainer -> {
            val size = preparation.containerSizeUnits()
            if (size > 0.0) simulatedSealedUnits / size else 0.0
        }

        else -> simulatedSealedUnits
    }
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
) {
    val effectiveCount = value.ifEmpty { placeholder }.toIntOrNull() ?: 0
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
                // label can sit immediately next to the number.
                Text(
                    text = value.ifEmpty { placeholder },
                    style = numberStyle,
                    color = if (value.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        Color.Transparent
                    },
                )
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(it.filter(Char::isDigit)) },
                    textStyle = numberStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
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
private fun adjustStockUnitLabel(preparation: MedicinePreparation): String {
    val unitRes = when (preparation) {
        is MedicinePreparation.InjectionMultiUseVial -> R.string.stock_unit_vials
        is MedicinePreparation.GelContainer -> R.string.stock_unit_containers
        else -> stockUnitRes(preparation)
    }
    return if (unitRes != null) stringResource(unitRes) else ""
}

private fun stepCountText(current: String, delta: Int): String {
    val next = (current.toIntOrNull() ?: 0) + delta
    return next.coerceAtLeast(0).toString()
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
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", value)
    }
}

private val AdjustSheetTab.labelRes: Int
    get() = when (this) {
        AdjustSheetTab.RECOUNT -> R.string.stock_adjust_tab_recount
        AdjustSheetTab.RECEIVED -> R.string.stock_adjust_tab_received
    }

private fun Double?.toEditableCountOrEmpty(): String {
    val value = this ?: return ""
    return value.toLong().toString()
}
