package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.medication.medicationCountIndicatorText
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.medication.rememberMedicationApplicationIcons
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import java.util.UUID

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun MedicationCard(
    medication: MedicationGroupMedicationItemUiState,
    groupColorKey: MedicationGroupColorKey,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(groupColorKey)
    val applicationTypeIcon = rememberMedicationApplicationIcons()
        .getValue(medication.details.applicationType)
    val medicationName = medicationDisplayName(medication.details)
    val supportingParts = listOfNotNull(
        stringResource(medication.details.applicationType.labelRes),
        medicationDoseText(medication.details),
        medicationCountIndicatorText(medication.count).takeIf { medication.count > 1 }
    )
    Column(modifier = modifier) {
        EditorSegmentedListItem(
            onClick = onClick,
            index = index,
            count = count,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = MaterialTheme.shapes.small,
                    color = groupColorScheme.primaryContainer,
                    contentColor = groupColorScheme.onPrimaryContainer
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = applicationTypeIcon,
                            contentDescription = stringResource(medication.details.applicationType.labelRes),
                            modifier = Modifier.size(20.dp)
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
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = supportingParts.joinToString(separator = " · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                ) {
                    IconButton(
                        onClick = onDeleteClick,
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
            medication = MedicationGroupMedicationItemUiState(
                localId = "preview-medication",
                persistedMedicationId = UUID.fromString("0a59d4fd-674c-4cda-9ea6-8a20e9c89283").toString(),
                details = MedicationDetails(
                    category = MedicationCategory.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL),
                    dose = MedicationDose.MgAsMedicine(1.0)
                ),
                count = 2
            ),
            groupColorKey = MedicationGroupColorKey.TEAL,
            onClick = { },
            onDeleteClick = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}
