package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstradiolEquivalentCalculatorTest {
    @Test
    fun calculates_oral_estradiol_valerate_equivalent() {
        val result = EstradiolEquivalentCalculator.calculate(
            medication = MedicationDetails(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
                dose = MedicationDose.MgAsMedicine(4.0)
            )
        )

        assertEquals(3.06, result ?: 0.0, 0.01)
    }

    @Test
    fun calculates_gel_equivalent_from_percent_and_weight() {
        val result = EstradiolEquivalentCalculator.calculate(
            medication = MedicationDetails(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.GEL,
                selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL_GEL),
                dose = MedicationDose.GelPercentAndWeight(
                    percent = 0.06,
                    weightGrams = 2.5
                )
            )
        )

        assertEquals(1.5, result ?: 0.0, 0.0001)
    }

    @Test
    fun patch_release_rate_does_not_produce_mg_equivalent() {
        val result = EstradiolEquivalentCalculator.calculate(
            medication = MedicationDetails(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.PATCH_ON,
                selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
                dose = MedicationDose.PatchReleaseRateMcgPerDay(100.0)
            )
        )

        assertNull(result)
    }

    @Test
    fun non_estradiol_category_returns_null() {
        val result = EstradiolEquivalentCalculator.calculate(
            medication = MedicationDetails(
                category = MedicationCategory.TESTOSTERONE,
                applicationType = MedicationApplicationType.INJECTION,
                selection = MedicationSelection.Custom("Testosterone enanthate"),
                dose = MedicationDose.MgAsMedicine(5.0)
            )
        )

        assertNull(result)
    }
}
