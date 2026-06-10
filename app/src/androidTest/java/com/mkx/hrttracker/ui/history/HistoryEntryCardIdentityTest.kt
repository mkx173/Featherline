package com.mkx.hrttracker.ui.history

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
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
import java.time.format.DateTimeFormatter
import java.util.UUID

class HistoryEntryCardIdentityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun replacingVisibleEntrySnapsLeadingIconPaletteToReplacementEntry() {
        val roseEntry = testLogEntry(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"),
            sourceGroupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"),
        )
        val tealEntry = testLogEntry(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"),
            sourceGroupUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"),
        )
        var visibleEntry by mutableStateOf(roseEntry)
        var visibleColorKey by mutableStateOf(MedicationGroupColorKey.ROSE)
        var expectedRoseColor = Color.Unspecified
        var expectedTealColor = Color.Unspecified

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                val roseColorScheme = rememberMedicationGroupColorScheme(
                    colorKey = MedicationGroupColorKey.ROSE,
                )
                val tealColorScheme = rememberMedicationGroupColorScheme(
                    colorKey = MedicationGroupColorKey.TEAL,
                )
                SideEffect {
                    expectedRoseColor = roseColorScheme.primaryContainer
                    expectedTealColor = tealColorScheme.primaryContainer
                }

                HistoryEntryCardItem(
                    entry = visibleEntry,
                    timeFormatter = DateTimeFormatter.ofPattern("HH:mm"),
                    groupColorKey = visibleColorKey,
                    isFromArchivedGroup = false,
                    isSelected = false,
                    isSelectionMode = false,
                    index = 0,
                    count = 1,
                    onClick = {},
                    onLongClick = {},
                    onSelectionClick = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MedicationCardLeadingIconTestTag, useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(
                    MedicationCardLeadingIconContainerColorArgbKey,
                    expectedRoseColor.toArgb(),
                ),
            )

        composeRule.runOnIdle {
            visibleEntry = tealEntry
            visibleColorKey = MedicationGroupColorKey.TEAL
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(MedicationCardLeadingIconTestTag, useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(
                    MedicationCardLeadingIconContainerColorArgbKey,
                    expectedTealColor.toArgb(),
                ),
            )
    }
}

private fun testLogEntry(
    uuid: UUID,
    sourceGroupUuid: UUID,
): MedicationLogEntry {
    return MedicationLogEntry(
        uuid = uuid,
        medicine = testMedicine(),
        category = MedicationCategory.ESTRADIOL,
        applicationType = MedicationApplicationType.ORAL,
        doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 1),
        equivalentE2Mg = 2.0,
        sourceGroupUuid = sourceGroupUuid,
        appliedAt = Instant.EPOCH,
    )
}

private fun testMedicine(): Medicine {
    val selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL)
    val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
    return Medicine(
        uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000001"),
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
