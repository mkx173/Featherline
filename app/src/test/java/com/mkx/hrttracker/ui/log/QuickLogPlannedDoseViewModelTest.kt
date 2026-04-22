package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickLogPlannedDoseViewModelTest {
    @Test
    fun buildCollapsedQuickLogDraftEntries_returns_single_entry_with_requested_count() {
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )

        val draftEntries = buildCollapsedQuickLogDraftEntries(
            details = details,
            count = 2,
            appliedDate = LocalDate.of(2026, 4, 18),
            appliedTime = LocalTime.of(9, 0)
        )

        assertEquals(1, draftEntries.size)
        assertEquals(2, draftEntries.single().count)
        assertEquals(details, draftEntries.single().details)
    }
}
