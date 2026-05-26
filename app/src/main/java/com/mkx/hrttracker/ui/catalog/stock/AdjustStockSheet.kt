package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import java.math.BigDecimal
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

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stock_adjust_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(16.dp))
            when (activeTab) {
                AdjustSheetTab.RECOUNT -> {
                    RecountForm(
                        projection = projection,
                        isContainer = isContainer,
                        onSubmit = onRecount,
                        onDismiss = onDismissRequest,
                    )
                }

                AdjustSheetTab.RECEIVED -> {
                    ReceivedForm(
                        projection = projection,
                        isContainer = isContainer,
                        onSubmit = onReceived,
                        onDismiss = onDismissRequest,
                    )
                }
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
    var unitsRemainingText by remember(
        projection.medicine.uuid,
        stock.unitsRemaining,
    ) { mutableStateOf(stock.unitsRemaining.toEditableTextOrEmpty()) }
    var unitsLastTotalText by remember(
        projection.medicine.uuid,
        stock.unitsLastTotal,
    ) { mutableStateOf(stock.unitsLastTotal.toEditableTextOrEmpty()) }

    val unitsRemaining = unitsRemainingText.toDoubleOrNull()
    val unitsLastTotal = unitsLastTotalText.optionalDoubleOrNull()
    val optionalPoolTotalValid = unitsLastTotalText.isBlankOrNonNegativeDecimal()
    val canConfirm = unitsRemaining != null &&
        unitsRemaining >= 0.0 &&
        optionalPoolTotalValid
    val simulatedTotal = if (isContainer) {
        // Recount touches sealed only; existing open container is preserved.
        val containerSize = projection.medicine.preparation.containerSizeUnits()
        val storedOpenAmount = (stock.openContainerAmount ?: 0.0).coerceIn(0.0, containerSize)
        (unitsRemaining ?: 0.0) * containerSize + storedOpenAmount
    } else {
        unitsRemaining ?: 0.0
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = unitsRemainingText,
            onValueChange = { unitsRemainingText = it },
            label = {
                Text(
                    if (isContainer) {
                        stringResource(R.string.stock_adjust_field_sealed)
                    } else {
                        stringResource(R.string.stock_adjust_field_now_have)
                    }
                )
            },
            keyboardOptions = decimalKeyboardOptions(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!isContainer) {
            OutlinedTextField(
                value = unitsLastTotalText,
                onValueChange = { unitsLastTotalText = it },
                label = {
                    Text(stringResource(R.string.stock_adjust_field_out_of_total))
                },
                keyboardOptions = decimalKeyboardOptions(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AfterPreview(
            projection = projection,
            simulatedTotalUnits = simulatedTotal,
        )
        SheetActionRow(
            canConfirm = canConfirm,
            onDismiss = onDismiss,
            onConfirm = {
                val resolvedUnitsRemaining = unitsRemaining ?: return@SheetActionRow
                onSubmit(
                    StockRecount(
                        unitsRemaining = resolvedUnitsRemaining,
                        unitsLastTotal = if (isContainer) null else unitsLastTotal,
                    )
                )
                onDismiss()
            },
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

    val received = receivedText.toDoubleOrNull()
    val canConfirm = received != null && received >= 0.0
    val simulatedTotal = if (isContainer) {
        // Received tops up sealed; the existing open container carries over
        // unchanged. Newly-tracked containers start with no open vial (null),
        // so the simulated total is sealed-only at opt-in time.
        val containerSize = projection.medicine.preparation.containerSizeUnits()
        val sealed = (projection.medicine.stock.unitsRemaining ?: 0.0) + (received ?: 0.0)
        val open = (projection.medicine.stock.openContainerAmount ?: 0.0)
            .coerceIn(0.0, containerSize)
        sealed * containerSize + open
    } else {
        storedTotalUnits(projection) + (received ?: 0.0)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = receivedText,
            onValueChange = { receivedText = it },
            label = {
                Text(
                    if (isContainer) {
                        stringResource(R.string.stock_adjust_field_sealed_received)
                    } else {
                        stringResource(R.string.stock_adjust_field_received)
                    }
                )
            },
            keyboardOptions = decimalKeyboardOptions(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AfterPreview(
            projection = projection,
            simulatedTotalUnits = simulatedTotal,
        )
        SheetActionRow(
            canConfirm = canConfirm,
            onDismiss = onDismiss,
            onConfirm = {
                val resolvedReceived = received ?: return@SheetActionRow
                onSubmit(StockReceived(unitsReceived = resolvedReceived))
                onDismiss()
            },
        )
    }
}

@Composable
private fun AfterPreview(
    projection: MedicineStockProjection,
    simulatedTotalUnits: Double,
) {
    val rate = projection.dosesPerDayMagnitude
    val runway = if (rate > 0.0) {
        stringResource(
            R.string.stock_runway_days_remaining,
            floor(simulatedTotalUnits / rate).toInt(),
        )
    } else {
        null
    }
    val label = if (runway != null) {
        stringResource(
            R.string.stock_adjust_after_with_runway,
            formatCount(simulatedTotalUnits),
            runway,
        )
    } else {
        stringResource(
            R.string.stock_adjust_after,
            formatCount(simulatedTotalUnits),
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SheetActionRow(
    canConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.stock_cancel))
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            enabled = canConfirm,
            onClick = onConfirm,
        ) {
            Text(stringResource(R.string.stock_adjust_confirm))
        }
    }
}

private fun decimalKeyboardOptions(): KeyboardOptions {
    return KeyboardOptions(keyboardType = KeyboardType.Decimal)
}

private fun String.optionalDoubleOrNull(): Double? {
    return trim().takeUnless(String::isEmpty)?.toDoubleOrNull()
}

private fun String.isBlankOrNonNegativeDecimal(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) return true
    val value = trimmed.toDoubleOrNull() ?: return false
    return value >= 0.0
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

private fun Double?.toEditableTextOrEmpty(): String {
    val value = this ?: return ""
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
