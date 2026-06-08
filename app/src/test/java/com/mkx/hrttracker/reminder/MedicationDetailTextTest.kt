package com.mkx.hrttracker.reminder

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationDetailTextTest {
    private val context: Context = mockk()
    private val realContext: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @Before
    fun stubContextResources() {
        // doseInstructionText/medicinePreparationSummary resolve the locale via
        // context.currentAppLocale(), which reads context.resources.configuration.
        // The strict mock has no Resources, so back it with the real Robolectric
        // resources (default locale) while getString stays per-test mocked.
        every { context.resources } returns realContext.resources
    }

    @Test
    fun buildExpandedDetailLines_belowCap_keepsAllLines() {
        val result = buildExpandedDetailLines(
            items = bundleItems(groupCount = 1, medicationsPerGroup = 3),
        ) { groupName, medication -> "$groupName · ${medication.uuid}" }

        assertEquals(3, result.visibleLines.size)
        assertEquals(0, result.hiddenCount)
    }

    @Test
    fun buildExpandedDetailLines_atCap_keepsAllLines() {
        val result = buildExpandedDetailLines(
            items = bundleItems(groupCount = 1, medicationsPerGroup = REMINDER_DETAIL_MAX_LINES),
        ) { groupName, medication -> "$groupName · ${medication.uuid}" }

        assertEquals(REMINDER_DETAIL_MAX_LINES, result.visibleLines.size)
        assertEquals(0, result.hiddenCount)
    }

    @Test
    fun buildExpandedDetailLines_aboveCap_truncatesAndReportsRemainder() {
        // Mix multiple groups so the cap respects the flat across-groups order.
        val result = buildExpandedDetailLines(
            items = bundleItems(groupCount = 2, medicationsPerGroup = 3),
        ) { groupName, medication -> "$groupName/${medication.uuid}" }

        assertEquals(REMINDER_DETAIL_MAX_LINES, result.visibleLines.size)
        assertEquals(6 - REMINDER_DETAIL_MAX_LINES, result.hiddenCount)
    }

    @Test
    fun buildExpandedDetailLines_empty_yieldsEmpty() {
        val result = buildExpandedDetailLines(items = emptyList()) { _, _ -> "" }

        assertEquals(emptyList<String>(), result.visibleLines)
        assertEquals(0, result.hiddenCount)
    }

    private fun bundleItems(
        groupCount: Int,
        medicationsPerGroup: Int,
    ): List<MedicationReminderBundleItem> {
        return (0 until groupCount).map { groupIndex ->
            MedicationReminderBundleItem(
                slot = MedicationReminderSlot(
                    groupUuid = UUID.randomUUID(),
                    scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
                    scheduleTimeUuid = UUID.randomUUID(),
                ),
                groupName = "Group$groupIndex",
                medications = (0 until medicationsPerGroup).map {
                    testMedicationGroupMedication(
                        medicine = testMedicine(),
                        applicationType = MedicationApplicationType.ORAL,
                        doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    )
                },
            )
        }
    }

    @Test
    fun capsulePreparationSummaryUsesCapsuleStrength() {
        every {
            context.getString(R.string.medication_preparation_summary_capsule, "100")
        } returns "100 mg per capsule"
        val medicine = testCustomMedicine(
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertEquals(
            "100 mg per capsule",
            com.mkx.hrttracker.util.medicinePreparationSummary(medicine, context),
        )
    }

    @Test
    fun medicationDetailLine_catalogMedication_singleCount() {
        every {
            context.getString(R.string.medication_name_estradiol_valerate)
        } returns "Estradiol valerate"
        every { context.getString(R.string.medication_application_oral) } returns "Oral"
        every { context.getString(R.string.unit_mg) } returns "mg"
        every {
            context.getString(R.string.dose_instruction_summary_active_amount, "2", "mg")
        } returns "2 mg"

        val medication = testMedicationGroupMedication(
            medicine = testMedicine(key = MedicationKey.ESTRADIOL_VALERATE),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = 1,
        )

        val result = medicationDetailLine(context, "Estrogens", medication)

        // A single whole tablet is implied by the active mg line; "1 tablet" portion is suppressed.
        assertEquals("Estrogens · Estradiol valerate · Oral · 2 mg", result)
    }

    @Test
    fun medicationDetailLine_foldsCountIntoDoseSummaryWhenGreaterThanOne() {
        every {
            context.getString(R.string.medication_name_spironolactone)
        } returns "Spironolactone"
        every { context.getString(R.string.medication_application_oral) } returns "Oral"
        every { context.getString(R.string.unit_mg) } returns "mg"
        every {
            context.getString(R.string.dose_instruction_summary_tablet_fraction, "2")
        } returns "2 tablets"
        every {
            context.getString(R.string.dose_instruction_summary_active_amount, "4", "mg")
        } returns "4 mg"

        val medication = testMedicationGroupMedication(
            medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = 2,
        )

        val result = medicationDetailLine(context, "Hormones", medication)

        assertEquals("Hormones · Spironolactone · Oral · 2 tablets · 4 mg", result)
    }

    @Test
    fun medicationDetailLine_capsulePreparationRendersActiveOnlyAtSingleCount() {
        every { context.getString(R.string.medication_application_oral) } returns "Oral"
        every { context.getString(R.string.unit_mg) } returns "mg"
        every {
            context.getString(R.string.dose_instruction_summary_active_amount, "100", "mg")
        } returns "100 mg"

        val medication = testMedicationGroupMedication(
            medicine = testCustomMedicine(
                medicationName = "Progesterone",
                preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            count = 1,
        )

        val result = medicationDetailLine(context, "Progesterone", medication)

        assertEquals("Progesterone · Progesterone · Oral · 100 mg", result)
    }

    @Test
    fun medicationDetailLine_capsuleCountShowsAggregateRealIntakeWithoutMultiplicityPrefix() {
        val medication = testMedicationGroupMedication(
            medicine = testCustomMedicine(
                medicationName = "Progesterone",
                preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 5.0),
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            count = 2,
        )

        val result = medicationDetailLine(realContext, "Progesterone", medication)

        assertEquals("Progesterone · Progesterone · Oral · 2 capsules · 10 mg", result)
    }

    @Test
    fun medicationDetailLine_omitsDoseSegmentForPatchOff() {
        // A PATCH_OFF slot carries no medicine — no dose segment is rendered.
        val medication = testMedicationGroupMedication(
            medicine = null,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            count = 1,
        )

        val result = medicationDetailLine(realContext, "Estrogens", medication)

        // Route falls back into the title for PATCH_OFF; don't duplicate the label.
        assertEquals("Estrogens · Remove patch", result)
    }
}
