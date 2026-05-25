package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.StockDiscard
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.data.repository.StockRecount
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.ui.catalog.AdjustSheetTab
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
    onDiscard: (StockDiscard) -> Unit,
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
            PrimaryTabRow(selectedTabIndex = activeTab.ordinal) {
                AdjustSheetTab.entries.forEach { tab ->
                    val enabled = !receivedOnly || tab == AdjustSheetTab.RECEIVED
                    Tab(
                        selected = tab == activeTab,
                        onClick = { if (enabled) activeTab = tab },
                        enabled = enabled,
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
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

                AdjustSheetTab.DISCARD -> {
                    DiscardForm(
                        projection = projection,
                        isContainer = isContainer,
                        onSubmit = onDiscard,
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
    var unitsRemainingText by remember { mutableStateOf("") }
    var unitsLastTotalText by remember { mutableStateOf("") }
    var openContainerText by remember { mutableStateOf("") }

    val unitsRemaining = unitsRemainingText.toDoubleOrNull()
    val unitsLastTotal = unitsLastTotalText.optionalDoubleOrNull()
    val openContainerAmount = openContainerText.optionalDoubleOrNull()
    val optionalPoolTotalValid = unitsLastTotalText.isBlankOrNonNegativeDecimal()
    val optionalOpenAmountValid = openContainerText.isBlankOrNonNegativeDecimal()
    val canConfirm = unitsRemaining != null &&
        unitsRemaining >= 0.0 &&
        optionalPoolTotalValid &&
        optionalOpenAmountValid
    val simulatedTotal = if (isContainer) {
        val containerSize = projection.medicine.preparation.containerSizeUnits()
        val storedOpenAmount = openContainerAmount
            ?.coerceIn(0.0, containerSize)
            ?: 0.0
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
        if (isContainer) {
            OutlinedTextField(
                value = openContainerText,
                onValueChange = { openContainerText = it },
                label = {
                    Text(stringResource(R.string.stock_adjust_field_open_remaining))
                },
                keyboardOptions = decimalKeyboardOptions(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
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
                        openContainerAmount = if (isContainer) openContainerAmount else null,
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
    var openContainerText by remember { mutableStateOf("") }

    val received = receivedText.toDoubleOrNull()
    val openContainerAmount = openContainerText.optionalDoubleOrNull()
    val optionalOpenAmountValid = openContainerText.isBlankOrNonNegativeDecimal()
    val canConfirm = received != null &&
        received >= 0.0 &&
        optionalOpenAmountValid
    val simulatedTotal = if (isContainer) {
        val containerSize = projection.medicine.preparation.containerSizeUnits()
        val sealed = (projection.medicine.stock.unitsRemaining ?: 0.0) + (received ?: 0.0)
        val open = openContainerAmount
            ?.coerceIn(0.0, containerSize)
            ?: projection.medicine.stock.openContainerAmount?.coerceIn(0.0, containerSize)
            ?: 0.0
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
        if (isContainer) {
            OutlinedTextField(
                value = openContainerText,
                onValueChange = { openContainerText = it },
                label = {
                    Text(stringResource(R.string.stock_adjust_field_open_remaining))
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
                val resolvedReceived = received ?: return@SheetActionRow
                onSubmit(
                    StockReceived(
                        unitsReceived = resolvedReceived,
                        openContainerAmount = if (isContainer) openContainerAmount else null,
                    )
                )
                onDismiss()
            },
        )
    }
}

@Composable
private fun DiscardForm(
    projection: MedicineStockProjection,
    isContainer: Boolean,
    onSubmit: (StockDiscard) -> Unit,
    onDismiss: () -> Unit,
) {
    var discardText by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(DiscardTarget.OPEN_CONTAINER) }

    val amount = discardText.toDoubleOrNull()
    val canConfirm = amount != null && amount >= 0.0
    val simulatedTotal = if (isContainer) {
        val containerSize = projection.medicine.preparation.containerSizeUnits()
        val currentSealed = projection.medicine.stock.unitsRemaining ?: 0.0
        val currentOpen = projection.medicine.stock.openContainerAmount ?: 0.0
        when (target) {
            DiscardTarget.OPEN_CONTAINER -> {
                currentSealed * containerSize + (currentOpen - (amount ?: 0.0)).coerceAtLeast(0.0)
            }

            DiscardTarget.SEALED -> {
                (currentSealed - (amount ?: 0.0)).coerceAtLeast(0.0) * containerSize + currentOpen
            }
        }
    } else {
        (projection.medicine.stock.unitsRemaining ?: 0.0)
            .minus(amount ?: 0.0)
            .coerceAtLeast(0.0)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isContainer) {
            DiscardTargetSelector(
                selected = target,
                onSelected = { target = it },
            )
        }
        OutlinedTextField(
            value = discardText,
            onValueChange = { discardText = it },
            label = {
                Text(stringResource(R.string.stock_adjust_field_discard_pool))
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
                val resolvedAmount = amount ?: return@SheetActionRow
                onSubmit(
                    if (!isContainer) {
                        StockDiscard.FromPool(units = resolvedAmount)
                    } else {
                        when (target) {
                            DiscardTarget.OPEN_CONTAINER -> {
                                StockDiscard.FromOpenContainer(amount = resolvedAmount)
                            }

                            DiscardTarget.SEALED -> {
                                StockDiscard.FromSealed(units = resolvedAmount)
                            }
                        }
                    }
                )
                onDismiss()
            },
        )
    }
}

@Composable
private fun DiscardTargetSelector(
    selected: DiscardTarget,
    onSelected: (DiscardTarget) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DiscardTarget.entries.forEach { target ->
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == target,
                        role = Role.RadioButton,
                        onClick = { onSelected(target) },
                    )
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(
                    selected = selected == target,
                    onClick = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(target.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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
        AdjustSheetTab.DISCARD -> R.string.stock_adjust_tab_discard
    }

private enum class DiscardTarget {
    OPEN_CONTAINER,
    SEALED,
}

private val DiscardTarget.labelRes: Int
    get() = when (this) {
        DiscardTarget.OPEN_CONTAINER -> R.string.stock_adjust_field_discard_open
        DiscardTarget.SEALED -> R.string.stock_adjust_field_discard_sealed
    }
