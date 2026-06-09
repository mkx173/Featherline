package com.mkx.hrttracker.ui.medication

import androidx.compose.ui.test.junit4.createComposeRule
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MedicationUiTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun formatTabletFraction_renders_integer_when_denominator_is_one() {
        assertEquals("1", formatTabletFraction(DoseInstruction.TabletFraction(1, 1)))
        assertEquals("3", formatTabletFraction(DoseInstruction.TabletFraction(3, 1)))
    }

    @Test
    fun formatTabletFraction_renders_ascii_fraction_otherwise() {
        assertEquals("1/2", formatTabletFraction(DoseInstruction.TabletFraction(1, 2)))
        assertEquals("1/4", formatTabletFraction(DoseInstruction.TabletFraction(1, 4)))
        assertEquals("3/4", formatTabletFraction(DoseInstruction.TabletFraction(3, 4)))
    }

    @Test
    fun patch_off_without_medicine_still_uses_application_type_as_title() {
        assertTrue(
            shouldUseApplicationTypeAsMedicationEntryTitle(
                hasMedicine = false,
                applicationType = MedicationApplicationType.PATCH_OFF,
            ),
        )
    }

    @Test
    fun unresolved_non_patch_off_entry_omits_application_type_from_supporting_text() {
        assertFalse(
            shouldIncludeApplicationTypeInSupportingText(
                hasMedicine = false,
                applicationType = MedicationApplicationType.ORAL,
            ),
        )
    }

    @Test
    fun resolved_oral_entry_includes_application_type_in_supporting_text() {
        assertTrue(
            shouldIncludeApplicationTypeInSupportingText(
                hasMedicine = true,
                applicationType = MedicationApplicationType.ORAL,
            ),
        )
    }

    @Test
    fun doseInstructionSummary_foldsTabletCountIntoAggregateText() {
        var summary: String? = null

        composeRule.setContent {
            summary = doseInstructionSummary(
                medicine = testCustomMedicine(
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 10.0),
                ),
                instruction = DoseInstruction.TabletFraction(1, 2),
                count = 3,
            )
        }
        composeRule.waitForIdle()

        assertEquals("1.5 tablets · 15 mg", summary)
    }

    @Test
    fun medicationEntrySupportingText_usesShortPatchRouteAndAggregatePatchDose() {
        var text: String? = null

        composeRule.setContent {
            text = medicationEntrySupportingText(
                medicine = testCustomMedicine(
                    preparation = MedicinePreparation.Patch(
                        MedicinePreparation.PatchSpecification.TotalMg(valueMg = 1.44),
                    ),
                ),
                doseInstruction = DoseInstruction.WholeUnit,
                applicationType = MedicationApplicationType.PATCH_ON,
                count = 2,
            )
        }
        composeRule.waitForIdle()

        assertEquals("Patch · 2 patches · 2.88 mg", text)
    }

    @Test
    fun doseInstructionSummary_singleCountKeepsNonCanonicalTabletFractionText() {
        var threeHalves: String? = null
        var twoHalves: String? = null

        composeRule.setContent {
            val medicine = testMedicine(
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 10.0),
            )
            threeHalves = doseInstructionSummary(
                medicine = medicine,
                instruction = DoseInstruction.TabletFraction(3, 2),
                count = 1,
            )
            twoHalves = doseInstructionSummary(
                medicine = medicine,
                instruction = DoseInstruction.TabletFraction(2, 2),
                count = 1,
            )
        }
        composeRule.waitForIdle()

        assertEquals("3/2 tablets · 15 mg", threeHalves)
        assertEquals("2/2 tablets · 10 mg", twoHalves)
    }
}
