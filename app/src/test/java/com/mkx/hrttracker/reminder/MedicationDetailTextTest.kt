package com.mkx.hrttracker.reminder

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationDetailTextTest {
    private val context: Context = mockk()

    @Test
    fun medicationDetailLine_catalogMedication_singleCount() {
        every { context.getString(R.string.medication_name_estradiol_valerate) } returns "Estradiol valerate"
        every { context.getString(R.string.medication_application_oral) } returns "Oral"
        every { context.getString(R.string.unit_mg) } returns "mg"
        every {
            context.getString(R.string.medication_dose_with_unit, any(), any())
        } returns "2 mg"

        val medication = testMedicationGroupMedication(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_VALERATE,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            count = 1,
        )

        val result = medicationDetailLine(context, "Estrogens", medication)

        assertEquals("Estrogens · Estradiol valerate · Oral · 2 mg", result)
    }

    @Test
    fun medicationDetailLine_appendsCountWhenGreaterThanOne() {
        every { context.getString(R.string.medication_name_spironolactone) } returns "Spironolactone"
        every { context.getString(R.string.medication_application_oral) } returns "Oral"
        every { context.getString(R.string.unit_mg) } returns "mg"
        every {
            context.getString(R.string.medication_dose_with_unit, any(), any())
        } returns "100 mg"

        val medication = testMedicationGroupMedication(
            details = testCatalogMedicationDetails(
                key = MedicationKey.SPIRONOLACTONE,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(100.0),
            ),
            count = 2,
        )

        val result = medicationDetailLine(context, "Hormones", medication)

        assertEquals("Hormones · Spironolactone · Oral · 100 mg · 2x", result)
    }

    @Test
    fun medicationDetailLine_omitsDoseSegmentWhenDoseIsNone() {
        every { context.getString(R.string.medication_name_estradiol_patch) } returns "Estradiol patch"
        every { context.getString(R.string.medication_application_patch_off) } returns "Patch off"

        val medication = testMedicationGroupMedication(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_PATCH,
                applicationType = MedicationApplicationType.PATCH_OFF,
                dose = MedicationDose.None,
            ),
            count = 1,
        )

        val result = medicationDetailLine(context, "Estrogens", medication)

        assertEquals("Estrogens · Estradiol patch · Patch off", result)
    }
}
