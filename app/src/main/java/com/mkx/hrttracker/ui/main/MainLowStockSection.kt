package com.mkx.hrttracker.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.StockStatusIndicator
import com.mkx.hrttracker.ui.medication.medicineDisplayName
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun MainLowStockSection(
    warnings: List<MedicineStockProjection>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onMedicineClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (warnings.isEmpty()) return

    val namesByUuid = warnings.associate { projection ->
        projection.medicine.uuid to medicineDisplayName(projection.medicine)
    }
    val sortedWarnings = warnings.sortedWith(
        compareBy<MedicineStockProjection>(
            { projection -> projection.state.severityOrder() },
            { projection -> namesByUuid.getValue(projection.medicine.uuid) },
        ),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        val headerStateDescription = stringResource(
            if (expanded) {
                R.string.home_low_stock_expanded_state
            } else {
                R.string.home_low_stock_collapsed_state
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = headerStateDescription }
                .clickable(
                    role = Role.Button,
                    onClick = { onExpandedChange(!expanded) },
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val title = if (expanded) {
                stringResource(R.string.home_low_stock_title)
            } else {
                stringResource(R.string.home_low_stock_title_with_count, sortedWarnings.size)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedWarnings.forEach { projection ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMedicineClick(projection.medicine.uuid) },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = namesByUuid.getValue(projection.medicine.uuid),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                StockStatusIndicator(
                                    projection = projection,
                                    showGauge = false,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun MedicineStockState.severityOrder(): Int {
    return when (this) {
        MedicineStockState.OUT -> 0
        MedicineStockState.IMMINENT -> 1
        MedicineStockState.USER_LOW -> 2
        MedicineStockState.HEALTHY,
        MedicineStockState.NO_RUNWAY,
        MedicineStockState.UNTRACKED -> Int.MAX_VALUE
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun MainLowStockSectionPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            MainLowStockSection(
                warnings = listOf(
                    previewProjection(
                        uuid = "00000000-0000-0000-0000-000000000041",
                        displayName = "Estradiol valerate",
                        state = MedicineStockState.OUT,
                    ),
                    previewProjection(
                        uuid = "00000000-0000-0000-0000-000000000042",
                        displayName = "Progesterone",
                        state = MedicineStockState.USER_LOW,
                    ),
                ),
                expanded = true,
                onExpandedChange = { },
                onMedicineClick = { },
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private fun previewProjection(
    uuid: String,
    displayName: String,
    state: MedicineStockState,
): MedicineStockProjection {
    val key = MedicationKey.ESTRADIOL
    val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
    val medicine = Medicine(
        uuid = UUID.fromString(uuid),
        selection = MedicineSelection.Catalog(key),
        category = key.category,
        preparation = preparation,
        displayName = displayName,
        identityKey = MedicineIdentityKey.catalog(key, preparation),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = MedicineStock(
            trackingEnabled = true,
            unitsRemaining = if (state == MedicineStockState.OUT) 0.0 else 4.0,
            unitsLastTotal = 10.0,
        ),
    )
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = medicine.stock.unitsRemaining ?: 0.0,
        runway = RunwayProjection.Days(
            days = if (state == MedicineStockState.OUT) 0 else 7,
            lastFulfillable = LocalDate.of(2026, 1, 8),
        ),
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = state,
    )
}
