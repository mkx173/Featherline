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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.medication.medicationEntrySupportingText
import com.mkx.hrttracker.ui.medication.medicationEntryTitle
import com.mkx.hrttracker.ui.medication.medicinePreparationIconRes
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.labelRes

internal const val MedicationCardLeadingIconTestTag = "medication-card-leading-icon"

internal val MedicationCardLeadingIconContainerColorArgbKey =
    SemanticsPropertyKey<Int>("MedicationCardLeadingIconContainerColorArgb")

internal var SemanticsPropertyReceiver.medicationCardLeadingIconContainerColorArgb by
    MedicationCardLeadingIconContainerColorArgbKey

internal enum class MedicationCardMissingGroupColorTreatment {
    PRIMARY_CONTAINER,
    NEUTRAL_GROUP_PALETTE,
}

internal fun medicationCardUsesGroupPalette(
    groupColorKey: MedicationGroupColorKey?,
    missingGroupColorTreatment: MedicationCardMissingGroupColorTreatment,
): Boolean {
    return groupColorKey != null ||
        missingGroupColorTreatment == MedicationCardMissingGroupColorTreatment.NEUTRAL_GROUP_PALETTE
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun MedicationCard(
    medicine: Medicine?,
    doseInstruction: DoseInstruction,
    applicationType: MedicationApplicationType,
    medicationCount: Int,
    groupColorKey: MedicationGroupColorKey?,
    missingGroupColorTreatment: MedicationCardMissingGroupColorTreatment =
        MedicationCardMissingGroupColorTreatment.PRIMARY_CONTAINER,
    // Null onClick renders a non-clickable static card (no ripple, no
    // disabled gray-out) — used for purely informational summary cards
    // such as the locked medicine on existing log entries.
    onClick: (() -> Unit)? = null,
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
    // Medicine-identity surfaces (medicine manager, medicine detail header,
    // editor summary) opt into the preparation-form glyph so a tablet reads
    // the same regardless of whether the current entry is oral or sublingual.
    // Dose surfaces leave this off and keep the per-route icon.
    leadingIconAsForm: Boolean = false,
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
    val useGroupPalette = medicationCardUsesGroupPalette(
        groupColorKey = groupColorKey,
        missingGroupColorTreatment = missingGroupColorTreatment,
    )
    // Most cards with no group identity use the app's primary container so
    // the icon still reads as a colored chip. Some entry surfaces, such as
    // manual logs opened from Home, opt into the neutral group palette to
    // match their source row.
    val leadingSurfaceColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        useGroupPalette -> groupColorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val leadingContentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        useGroupPalette -> groupColorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val leadingIconModifier = Modifier
        .size(36.dp)
        .testTag(MedicationCardLeadingIconTestTag)
        .semantics {
            medicationCardLeadingIconContainerColorArgb = leadingSurfaceColor.toArgb()
        }
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
        containerColor = containerColor
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
                    } else if (leadingIconAsForm && medicine != null) {
                        Icon(
                            painter = painterResource(
                                medicinePreparationIconRes(medicine.preparation),
                            ),
                            contentDescription = leadingIconContentDescription ?: applicationTypeLabel,
                            modifier = Modifier.size(20.dp),
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
                )
            }
            resolvedTrailingContent?.let {
                Spacer(modifier = Modifier.width(12.dp))
                it.invoke()
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
