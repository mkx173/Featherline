package com.mkx.hrttracker.ui.medication

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.ui.components.MedicationCardLeadingIconContainerColorArgbKey
import com.mkx.hrttracker.ui.components.MedicationCardLeadingIconTestTag
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MedicationLogEntryLinkedMedicationSummaryColorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manualLogLinkedSummaryUsesNeutralLeadingIconPaletteWhenGroupColorIsMissing() {
        var expectedNeutralColor = Color.Unspecified
        var primaryContainerColor = Color.Unspecified

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                val neutralScheme = rememberMedicationGroupColorScheme(colorKey = null)
                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    expectedNeutralColor = neutralScheme.primaryContainer
                    primaryContainerColor = colorScheme.primaryContainer
                }
                MedicationLogEntryLinkedMedicationSummary(
                    lockedMedicine = testMedicine(),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.Noop,
                    countText = "1",
                    sourceGroupName = null,
                    sourceGroupColorKey = null,
                    sourceGroupIsArchived = false,
                    sourceGroupScheduledForText = null,
                    sourceGroupScheduleOffsetText = null,
                    sourceGroupScheduleOffsetOutsideFulfillmentWindow = false,
                )
            }
        }

        composeRule.onNodeWithTag(MedicationCardLeadingIconTestTag, useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(
                    MedicationCardLeadingIconContainerColorArgbKey,
                    expectedNeutralColor.toArgb(),
                ),
            )
            .assert(
                SemanticsMatcher(
                    description = "leading icon container is not Material primaryContainer",
                ) { node ->
                    node.config[MedicationCardLeadingIconContainerColorArgbKey] !=
                            primaryContainerColor.toArgb()
                },
            )
    }
}

private fun testMedicine(): Medicine {
    val selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL)
    val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
    return Medicine(
        uuid = UUID.fromString("00000000-0000-0000-0000-000000000041"),
        selection = selection,
        category = MedicationCategory.ESTRADIOL,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = preparation,
        ),
        displayDoseUnit = MedicineDisplayDoseUnit.MG,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = MedicineStock(),
    )
}
