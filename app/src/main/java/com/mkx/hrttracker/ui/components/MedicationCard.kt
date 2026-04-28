package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ColorScheme
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
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.ui.medication.MedicationApplicationIcon
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationSupportingText
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun MedicationCard(
    details: MedicationDetails,
    medicationCount: Int,
    groupColorKey: MedicationGroupColorKey?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    extraSupportingText: String? = null,
    colorScheme: ColorScheme? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
    index: Int = 0,
    itemCount: Int = 1
) {
    val fallbackColorScheme = rememberMedicationGroupColorScheme(groupColorKey)
    val groupColorScheme = colorScheme ?: fallbackColorScheme
    val applicationTypeLabel = stringResource(details.applicationType.labelRes)
    val medicationName = medicationDisplayName(details)
    val supportingText = medicationSupportingText(
        details = details,
        medicationCount = medicationCount,
        extraSupportingText = extraSupportingText
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
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = groupColorScheme.secondaryContainer,
                contentColor = groupColorScheme.onSecondaryContainer
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MedicationApplicationIcon(
                        applicationType = details.applicationType,
                        contentDescription = applicationTypeLabel,
                        modifier = Modifier.size(20.dp),
                        scheduleIconSize = 9.dp,
                    )
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
            details = previewMedicationCardDetails(
                applicationType = MedicationApplicationType.ORAL,
                medicationKey = MedicationKey.ESTRADIOL,
                dose = MedicationDose.MgAsMedicine(1.0)
            ),
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
            details = previewMedicationCardDetails(
                applicationType = MedicationApplicationType.SUBLINGUAL,
                medicationKey = MedicationKey.ESTRADIOL,
                dose = MedicationDose.MgAsMedicine(1.0)
            ),
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

private fun previewMedicationCardDetails(
    applicationType: MedicationApplicationType,
    medicationKey: MedicationKey,
    dose: MedicationDose,
): MedicationDetails {
    return MedicationDetails(
        category = MedicationCategory.ESTRADIOL,
        applicationType = applicationType,
        selection = MedicationSelection.Catalog(medicationKey),
        dose = dose
    )
}
