package com.mkx.hrttracker.ui.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationLogEntryEditorSheetTest {
    @Test
    fun summaryDoseAmountDelta_ignoresInteractiveRulerDeltaUntilLogIsSaved() {
        val summaryDelta = medicationLogEntrySummaryDoseAmountDelta(
            allowsActualDoseDelta = true,
            showActualDoseDeltaReadOnly = false,
            doseAmountDelta = 0.05,
        )

        assertNull(summaryDelta)
    }

    @Test
    fun summaryDoseAmountDelta_keepsLoggedDeltaWhenEditingReadOnlyActualAmount() {
        val summaryDelta = medicationLogEntrySummaryDoseAmountDelta(
            allowsActualDoseDelta = false,
            showActualDoseDeltaReadOnly = true,
            doseAmountDelta = 0.05,
        )

        assertEquals(0.05, summaryDelta ?: 0.0, 1e-9)
    }
}
