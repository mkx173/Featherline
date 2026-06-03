package com.mkx.hrttracker.ui.main

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.StockStatusIndicator
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.medication.medicineDisplayName
import com.mkx.hrttracker.ui.medication.medicinePreparationIconRes
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { onExpandedChange(!expanded) }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            MainLowStockCardHeader(
                warningCount = sortedWarnings.size,
                expanded = expanded,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
                ) {
                    sortedWarnings.forEachIndexed { index, projection ->
                        MainLowStockSubCard(
                            projection = projection,
                            medicineName = namesByUuid.getValue(projection.medicine.uuid),
                            index = index,
                            itemCount = sortedWarnings.size,
                            onMedicineClick = onMedicineClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainLowStockCardHeader(
    warningCount: Int,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val headerStateDescription = stringResource(
        if (expanded) {
            R.string.home_low_stock_expanded_state
        } else {
            R.string.home_low_stock_collapsed_state
        },
    )
    val title = stringResource(R.string.home_low_stock_title_with_count, warningCount)
    val chevronRotation = animateFloatAsState(
        targetValue = mainLowStockExpandChevronTargetRotation(expanded),
        label = "LowStockExpandChevronRotation",
    ).value

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = headerStateDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_inventory_2),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).cjkTextOffset(title),
        )

        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                rotationZ = chevronRotation
            },
        )
    }
}

@Composable
private fun MainLowStockSubCard(
    projection: MedicineStockProjection,
    medicineName: String,
    index: Int,
    itemCount: Int,
    onMedicineClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val supportingText = mainLowStockRunwaySupportingText(projection.runway).resolve()

    EditorSegmentedListItem(
        index = index,
        count = itemCount,
        onClick = { onMedicineClick(projection.medicine.uuid) },
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        cornerShape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            medicinePreparationIconRes(projection.medicine.preparation),
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.cjkTextOffset(medicineName),
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.cjkTextOffset(supportingText),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StockStatusIndicator(projection = projection)
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

internal data class MainLowStockRunwaySupportingText(
    @param:StringRes val resId: Int? = null,
    @param:PluralsRes val pluralResId: Int? = null,
    val intArg: Int? = null,
)

internal fun mainLowStockRunwaySupportingText(
    runway: RunwayProjection,
): MainLowStockRunwaySupportingText {
    return when (runway) {
        is RunwayProjection.Days -> MainLowStockRunwaySupportingText(
            pluralResId = R.plurals.stock_runway_days_remaining,
            intArg = runway.days,
        )
        RunwayProjection.BeyondHorizon -> MainLowStockRunwaySupportingText(
            resId = R.string.stock_runway_more_than_one_year,
        )
        RunwayProjection.NoSchedule -> MainLowStockRunwaySupportingText(
            resId = R.string.stock_runway_unknown_title,
        )
    }
}

internal fun mainLowStockExpandChevronTargetRotation(expanded: Boolean): Float {
    return if (expanded) 180f else 0f
}

@Composable
private fun MainLowStockRunwaySupportingText.resolve(): String {
    return when {
        pluralResId != null && intArg != null ->
            pluralStringResource(pluralResId, intArg, intArg)
        resId != null -> stringResource(resId)
        else -> ""
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
