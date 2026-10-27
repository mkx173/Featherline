package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WidgetDoseRowTrailingContentTest {
    @Test
    fun manualRowsHaveNoTrailingText() {
        assertEquals(null, widgetDoseRowTrailingText(widgetDoseRow("Manual", isManualRecord = true)))
    }

    @Test
    fun scheduledRowsKeepTrailingText() {
        assertEquals("08:00", widgetDoseRowTrailingText(widgetDoseRow("08:00", isManualRecord = false)))
    }

    private fun widgetDoseRow(
        trailingText: String?,
        isManualRecord: Boolean,
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
            contextChip = null,
            groupUuid = "group-1",
            scheduleTimeUuid = "time-1",
        )
    }
}
