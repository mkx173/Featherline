package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testCustomMedicationDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class MainRowDecorTest {
    @Test
    fun mainMedicationAccent_returns_secondary_for_antiandrogen() {
        val details = testCatalogMedicationDetails(
            key = MedicationKey.SPIRONOLACTONE,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(100.0)
        )

        assertEquals(MainMedicationAccent.SECONDARY, mainMedicationAccent(details))
    }

    @Test
    fun mainMedicationAccent_returns_tertiary_for_transdermal_estradiol() {
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL_PATCH,
            applicationType = MedicationApplicationType.PATCH_ON,
            dose = MedicationDose.PatchReleaseRateMcgPerDay(100.0)
        )

        assertEquals(MainMedicationAccent.TERTIARY, mainMedicationAccent(details))
    }

    @Test
    fun mainMedicationAccent_returns_primary_for_non_transdermal_estradiol() {
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL_VALERATE,
            applicationType = MedicationApplicationType.INJECTION,
            dose = MedicationDose.MgAsMedicine(5.0)
        )

        assertEquals(MainMedicationAccent.PRIMARY, mainMedicationAccent(details))
    }

    @Test
    fun mainMedicationAccent_returns_neutral_for_custom_medication() {
        val details = testCustomMedicationDetails(
            medicationName = "Custom oral medication",
            dose = MedicationDose.MgAsMedicine(1.0)
        )

        assertEquals(MainMedicationAccent.NEUTRAL, mainMedicationAccent(details))
    }

    @Test
    fun mainTodayRowTone_matches_today_status() {
        assertEquals(MainTodayRowTone.DONE, mainTodayRowTone(MainTodayDoseStatus.DONE))
        assertEquals(MainTodayRowTone.DUE_SOON, mainTodayRowTone(MainTodayDoseStatus.DUE_SOON))
        assertEquals(MainTodayRowTone.DEFAULT, mainTodayRowTone(MainTodayDoseStatus.UPCOMING))
        assertEquals(MainTodayRowTone.OVERDUE, mainTodayRowTone(MainTodayDoseStatus.OVERDUE))
    }
}
