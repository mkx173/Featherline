package com.mkx.hrttracker.widget

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WidgetDoseRowTrailingContentTest {
    @Test
    fun manualRowsUseIconInsteadOfTrailingTextWhenDetailsAreVisible() {
        val row = widgetDoseRow(
            trailingText = "Manual",
            isManualRecord = true,
        )

        assertTrue(widgetDoseRowShowsManualTrailingIcon(row, hideMedicationDetails = false))
        assertEquals(
            R.drawable.ic_edit_square,
            widgetDoseRowTrailingIconDrawableRes(row, hideMedicationDetails = false),
        )
        assertEquals(null, widgetDoseRowTrailingText(row, hideMedicationDetails = false))
    }

    @Test
    fun manualRowsUseIconInsteadOfTrailingTextWhenDetailsAreHidden() {
        val row = widgetDoseRow(
            trailingText = "Manual",
            isManualRecord = true,
        )

        assertTrue(widgetDoseRowShowsManualTrailingIcon(row, hideMedicationDetails = true))
        assertEquals(
            R.drawable.ic_edit_square,
            widgetDoseRowTrailingIconDrawableRes(row, hideMedicationDetails = true),
        )
        assertEquals(null, widgetDoseRowTrailingText(row, hideMedicationDetails = true))
    }

    @Test
    fun importedRowsUseDownloadIconInsteadOfTrailingText() {
        val row = widgetDoseRow(
            trailingText = "Manual",
            isManualRecord = true,
            isImportedRecord = true,
        )

        assertEquals(
            R.drawable.ic_download,
            widgetDoseRowTrailingIconDrawableRes(row, hideMedicationDetails = false),
        )
        assertEquals(null, widgetDoseRowTrailingText(row, hideMedicationDetails = false))
    }

    @Test
    fun scheduledRowsKeepTrailingText() {
        val row = widgetDoseRow(
            trailingText = "08:00",
            isManualRecord = false,
        )

        assertFalse(widgetDoseRowShowsManualTrailingIcon(row, hideMedicationDetails = false))
        assertEquals(null, widgetDoseRowTrailingIconDrawableRes(row, hideMedicationDetails = false))
        assertEquals("08:00", widgetDoseRowTrailingText(row, hideMedicationDetails = false))
    }

    private fun widgetDoseRow(
        trailingText: String?,
        isManualRecord: Boolean,
        isImportedRecord: Boolean = false,
    ): WidgetDoseRow {
        return WidgetDoseRow(
            medicationName = "Estradiol",
            groupName = "Morning",
            colorKey = MedicationGroupColorKey.ROSE,
            routeLabel = "Oral",
            doseText = "2 mg",
            status = WidgetDoseStatus.DONE,
            scheduledAt = LocalDateTime.of(2026, 1, 1, 9, 0),
            trailingText = trailingText,
            isManualRecord = isManualRecord,
            isImportedRecord = isImportedRecord,
            contextChip = null,
            groupUuid = "group-1",
            scheduleTimeUuid = "time-1",
        )
    }
}
