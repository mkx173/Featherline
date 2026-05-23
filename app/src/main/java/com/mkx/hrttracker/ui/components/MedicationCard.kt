package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.medication.medicationEntrySupportingText
import com.mkx.hrttracker.ui.medication.medicationEntryTitle
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun MedicationCard(
    medicine: Medicine?,
    doseInstruction: DoseInstruction,
    applicationType: MedicationApplicationType,
    medicationCount: Int,
    groupColorKey: MedicationGroupColorKey?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    extraSupportingText: String? = null,
    supportingTextOverride: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    isSelected: Boolean = false,
    onLeadingIconClick: (() -> Unit)? = null,
    leadingIconContentDescription: String? = null,
    enabled: Boolean = true,
    index: Int = 0,
    itemCount: Int = 1
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(colorKey = groupColorKey)
    val applicationTypeLabel = stringResource(applicationType.labelRes)
    val medicationName = medicationEntryTitle(medicine, applicationType)
    // The medicine manager describes a medicine, not an entry — its supporting
    // line should be the preparation summary, not the route/dose. Other callers
    // (slot card, log card) keep the entry-shaped text.
    val supportingText = supportingTextOverride
        ?: medicationEntrySupportingText(
            medicine = medicine,
            doseInstruction = doseInstruction,
            applicationType = applicationType,
            count = medicationCount,
            extraSupportingText = extraSupportingText
        )
    // No group color → no group identity to express. Fall back to the app's
    // primary container so the icon still reads as a colored chip; the slate
    // group palette renders as a gray that visually disappears against the
    // card.
    val leadingSurfaceColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        groupColorKey == null -> MaterialTheme.colorScheme.primaryContainer
        else -> groupColorScheme.primaryContainer
    }
    val leadingContentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        groupColorKey == null -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> groupColorScheme.onPrimaryContainer
    }
    val leadingIconModifier = Modifier
        .size(36.dp)
        .then(
            if (onLeadingIconClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    onClick = onLeadingIconClick
                )
            } else {
                Modifier
            }
        )
    val resolvedTrailingContent = trailingContent ?: onDeleteClick?.let { deleteClick ->
        @Composable {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified
            ) {
                IconButton(
                    onClick = deleteClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.remove_medication_from_group)
                    )
                }
            }
        }
    }

    EditorSegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        index = index,
        count = itemCount,
        modifier = modifier,
        enabled = enabled,
        containerColor = containerColor,
        trailingContent = resolvedTrailingContent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            ) {
            Surface(
                modifier = leadingIconModifier,
                shape = MaterialTheme.shapes.small,
                color = leadingSurfaceColor,
                contentColor = leadingContentColor
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = leadingIconContentDescription,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        MedicationApplicationIcon(
                            applicationType = applicationType,
                            contentDescription = leadingIconContentDescription ?: applicationTypeLabel,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.cjkTextOffset(medicationName),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.cjkTextOffset(supportingText),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(
    name = "Medication Card",
    showBackground = true,
    widthDp = 420
)
@Composable
private fun MedicationCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationCard(
            medicine = previewMedicine(MedicationKey.ESTRADIOL),
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
            applicationType = MedicationApplicationType.ORAL,
            medicationCount = 2,
            groupColorKey = MedicationGroupColorKey.TEAL,
            onClick = { },
            onDeleteClick = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(
    name = "History Medication Card",
    showBackground = true,
    widthDp = 420
)
@Composable
private fun HistoryMedicationCardPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationCard(
            medicine = previewMedicine(MedicationKey.ESTRADIOL),
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
            applicationType = MedicationApplicationType.SUBLINGUAL,
            medicationCount = 2,
            groupColorKey = MedicationGroupColorKey.INDIGO,
            extraSupportingText = "Nightly estradiol",
            onClick = { },
            trailingContent = {
                Text(
                    text = "19:00",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun previewMedicine(medicationKey: MedicationKey): Medicine {
    val selection = MedicineSelection.Catalog(medicationKey)
    val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
    return Medicine(
        uuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
        selection = selection,
        category = medicationKey.category,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
        createdAt = java.time.Instant.EPOCH,
        updatedAt = java.time.Instant.EPOCH,
        archivedAt = null,
    )
}
